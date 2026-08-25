package vn.asg.swim.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.model.ResolvedAddressing;
import vn.asg.swim.repository.GwinRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nhận bản tin SWIM, kiểm tra hợp lệ và lưu vào bảng gwin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AMQPSubscriberService {

    private final ConnectionManagerService connectionManager;
    private final RoutingService routingService;
    private final MessageConversionService conversionService;
    private final AlertService alertService;
    private final GwinRepository gwinRepository;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final AtsmhsServiceLevelResolver atsmhsResolver;
    private final ConfigService configService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // Tập hợp an toàn đa luồng để tránh ConcurrentModificationException khi
    // subscribe/unsubscribe đồng thời
    private final List<Session> activeSessions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile List<String> currentSubscribedQueues = new CopyOnWriteArrayList<>();

    /**
     * Bắt đầu tiến trình subscribe bất đồng bộ sau khi khởi tạo.
     */
    @PostConstruct
    public void startSubscribing() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            subscribeAll();
        });
        t.setDaemon(true);
        t.setName("amqp-subscriber-init");
        t.start();
    }

    /**
     * Thực hiện subscribe toàn bộ các queue/topic inbound đang hoạt động.
     */
    public synchronized void subscribeAll() {
        if (!connectionManager.getConnected().get()) {
            log.warn("AMQP not connected, subscriber will retry later");
            return;
        }

        stopAll();
        running.set(true);

        List<String> queues = routingService.getActiveInboundTopics();
        if (queues.isEmpty()) {
            log.warn("No active inbound queues found for subscription");
            currentSubscribedQueues.clear();
            return;
        }

        for (String queue : queues) {
            subscribeQueue(queue);
        }
        currentSubscribedQueues = new CopyOnWriteArrayList<>(queues);
        log.info("Subscribed to {} queues: {}", queues.size(), queues);
    }

    /**
     * Subscribe queue: khởi tạo session và consumer, giải phóng nếu lỗi.
     */
    private void subscribeQueue(String queue) {
        Session session = null;
        try {
            session = connectionManager.createSession();
            MessageConsumer consumer = connectionManager.createConsumer(session, queue);
            consumer.setMessageListener(msg -> {
                try {
                    handleMessage(msg, queue);
                } catch (Exception e) {
                    log.error("Error handling AMQP message from queue {}: {}", queue, e.getMessage(), e);
                    // Bản tin bị loại ở đây là bị mất hẳn (không có bản ghi gwin nào để tham chiếu).
                    // Chỉ log.error là không đủ: EUR Doc 047 đòi mọi bản tin bị loại phải được báo
                    // lên Control Position, nên luôn phát alert kể cả khi không biết msgid.
                    try {
                        alertService.create(
                                GwAlert.TYPE_MESSAGE_DEAD,
                                GwAlert.SEV_ERROR,
                                "[SWIM->AMHS] Message dropped, unhandled error on queue " + queue + ": "
                                        + e.getMessage(),
                                "gwin", null);
                    } catch (Exception alertFailure) {
                        log.error("Failed to raise Control Position alert for dropped message: {}",
                                alertFailure.getMessage());
                    }
                }
            });

            // Chỉ add vào list khi subscribe thành công
            activeSessions.add(session);
            log.debug("Subscribed successfully to queue: {}", queue);

        } catch (JMSException e) {
            log.error("Failed to subscribe to queue {}: {}", queue, e.getMessage());
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Kiểm tra tính hợp lệ bản tin AMQP, phân giải địa chỉ và ghi vào Gwin.
     */
    @Transactional
    public void handleMessage(Message amqpMsg, String queue) throws JMSException {
        // Content-type phai doc TRUOC khi phan loai payload: §4.5.1.6(a) coi
        // "application/octet-stream" la khai bao binary co tinh quyet dinh, heuristic byte khong
        // duoc phep ghi de no.
        String contentType = getMsgProperty(amqpMsg, "content-type");
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "contentType");
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = getMsgProperty(amqpMsg, "content_type");
        }
        boolean declaredBinary = contentType != null
                && contentType.toLowerCase().contains("application/octet-stream");

        String textPayload = null;
        byte[] binaryPayload = null;
        // CTSW110 ban tin 2 (§4.5.1.6.b): "data" + content-type text/plain KHONG ve toi duoi dang
        // BytesMessage. Qpid AmqpCodec.createWithoutAnnotation() map Data + content-type text/* ->
        // AmqpJmsTextMessageFacade, va getText() decode bang charset.newDecoder() (action REPORT)
        // nen bytes khong hop le UTF-8 se nem JMSException ngay tai day. Neu de exception thoat ra,
        // listener chi log.error -> khong co ban ghi gwin, khong co alert, KHONG bao len Control
        // Position, ban tin bien mat. Bat lai de di vao duong reject chuan.
        String bodyDecodeError = null;
        if (amqpMsg instanceof TextMessage tm) {
            try {
                textPayload = tm.getText();
            } catch (JMSException e) {
                // Giu <= 64 ky tu: message_conversion_log.rejection_reason la varchar(64).
                bodyDecodeError = "content-type/content mismatch: payload is not valid UTF-8";
                log.warn("AMQP: content-type declares text but body bytes are not decodable as declared charset: {}",
                        e.getMessage());
            }
        } else if (amqpMsg instanceof BytesMessage bm) {
            byte[] buf = new byte[(int) bm.getBodyLength()];
            bm.readBytes(buf);
            if (declaredBinary) {
                // CTSW103 ban tin 2: PDF/FTBP nho phan lon la ASCII in duoc (%PDF-1.4,
                // /Type/Catalog, endobj) nen ty le ky tu control tut xuong duoi nguong 5% cua
                // isProbablyText -> bi phan loai nham thanh text. Hau qua: binaryPayload=null nen
                // (1) check BASIC-khong-nhan-binary bi bo qua hoan toan, (2) payload bi UTF-8
                // decode lam hong byte, (3) nhanh giai nen gzip khong chay. content-type da khai
                // binary thi tin content-type, khong chay heuristic.
                binaryPayload = buf;
            } else if (buf.length >= 2 && ((buf[0] == (byte) 0xFF && buf[1] == (byte) 0xFE) || (buf[0] == (byte) 0xFE && buf[1] == (byte) 0xFF))) {
                textPayload = new String(buf, StandardCharsets.UTF_16);
            } else {
                String text = new String(buf, StandardCharsets.UTF_8);
                if (text.stripLeading().startsWith("<") || isProbablyText(text.stripLeading())) {
                    textPayload = text;
                } else {
                    // Noi dung binary that (FTBP) - KHONG gan textPayload (UTF-8 decode se lam hong
                    // byte khong hop le UTF-8 bang ky tu thay the U+FFFD, mat du lieu vinh vien).
                    // finalContent phai lay tu nhanh base64(binaryPayload) ben duoi.
                    binaryPayload = buf;
                }
            }
        }

        String finalContent = textPayload != null ? textPayload
                : (binaryPayload != null ? Base64.getEncoder().encodeToString(binaryPayload) : null);

        String swimCompression = getAppProperty(amqpMsg, "swim_compression");
        if (swimCompression == null || swimCompression.isBlank()) {
            swimCompression = getAppProperty(amqpMsg, "swim-compression");
        }

        if ("gzip".equalsIgnoreCase(swimCompression) && binaryPayload != null && binaryPayload.length > 0) {
            // CTSW116: nội dung sau khi giải nén vẫn là binary (FTBP file) - encode base64 để lưu an toàn,
            // KHÔNG decode UTF-8 (sẽ làm hỏng dữ liệu nếu byte không hợp lệ UTF-8).
            binaryPayload = decompressGzip(binaryPayload);
            finalContent = Base64.getEncoder().encodeToString(binaryPayload);
        }


        // Trích xuất các trường dữ liệu tiêu chuẩn (contentType đã đọc ở đầu hàm)
        // EUR Doc 047 v3.0 §4.4.3.3.3 / §4.5.1.6: chỉ 2 content-type hợp lệ. Không cross-check với
        // loại JMS message (Text/BytesMessage): Qpid AmqpCodec map CẢ "data + content-type text/*"
        // LẪN "amqp-value(String)" về cùng TextMessage, nên JMS message type không phân biệt được
        // amqp-value với data. Assertion cấu trúc của §4.5.1.6.b vì thế chỉ kiểm được gián tiếp qua
        // việc body có decode được theo charset đã khai hay không (bodyDecodeError ở trên).
        boolean contentTypeSupported = (bodyDecodeError == null);
        // Ly do tu choi cu the - khong gop chung thanh "Unsupported content-type" vi 3 nguyen nhan
        // khac han nhau (thieu / gia tri khong ho tro / charset / content khong khop khai bao).
        String contentTypeError = bodyDecodeError;
        if (contentType == null || contentType.isBlank()) {
            // CTSW102: Content-type is mandatory for AMHS-unaware service level
            contentTypeSupported = false;
            contentTypeError = "Mandatory field 'content-type' is missing";
            log.warn("AMQP: Mandatory content-type property is missing");
        } else {
            String ct = contentType.toLowerCase();
            if (!ct.contains("text/plain") && !ct.contains("application/octet-stream")) {
                contentTypeSupported = false;
                contentTypeError = "Unsupported content-type: " + contentType;
                log.warn("AMQP: Unsupported content-type '{}'", contentType);
            }
            if (ct.contains("utf-16")) {
                // CTSW110: utf-16 is unsupported
                contentTypeSupported = false;
                contentTypeError = "Unsupported charset in content-type (only utf-8): " + contentType;
                log.warn("AMQP: Unsupported charset utf-16 in content-type '{}'", contentType);
            }
            if (ct.contains("text/")) {
                // content-type=text/* quyet dinh, khong dua theo heuristic isProbablyText. Dung
                // strict UTF-8 decode (bao loi thay vi am tham thay U+FFFD) de phan biet "text hop
                // le" voi "content-type khai sai, bytes khong phai UTF-8" (mismatch that -> reject).
                if (binaryPayload != null) {
                    try {
                        finalContent = strictUtf8Decode(binaryPayload);
                        binaryPayload = null;
                    } catch (java.nio.charset.CharacterCodingException e) {
                        contentTypeSupported = false;
                        // Giu <= 64 ky tu: message_conversion_log.rejection_reason la varchar(64).
                        // Gia tri content-type day du da co o cot gwin.content_type.
                        contentTypeError = "content-type/content mismatch: payload is not valid UTF-8";
                        log.warn("AMQP: content-type declares text/plain but payload bytes are not valid UTF-8 (content-type/content mismatch)");
                    }
                }
                if (finalContent != null) {
                    finalContent = finalContent.replace("\u0000", "");
                }
            }
        }

        // Body decode that bai thi finalContent tat nhien null - dung bao them "thieu data/amqp-value"
        // vi ly do that la mismatch content-type/content, bao ca hai se lam sai lech chan doan.
        boolean dataValid = bodyDecodeError != null || (finalContent != null && !finalContent.isBlank());

        // 1. Trích xuất messageId — chỉ từ header/properties tầng AMQP, không bao giờ lấy từ payload nghiệp vụ
        String rawMsgId = cleanAmqpMessageId(amqpMsg.getJMSMessageID());
        if (rawMsgId == null) {
            String[] properties = { "message_id", "messageId", "amhs_message_id" };
            for (String property : properties) {
                rawMsgId = cleanAmqpMessageId(safeGetStringProperty(amqpMsg, property));
                if (rawMsgId != null) {
                    break;
                }
            }
        }

        boolean hasMessageId = (rawMsgId != null);
        String amqpMsgId = rawMsgId;
        log.info("Received AMQP message: {} from topic: {}", amqpMsgId, queue);

        // Chống lặp bản tin (Loopback prevention)
        String originGw = amqpMsg.getStringProperty("amhs_gateway_id");
        if (configService.getGatewayId().equals(originGw)) {
            log.info("Loopback detected for message {}. Dropping message to prevent infinite loop.", amqpMsgId);
            return;
        }

        // Loại bỏ bản tin trùng lặp (Deduplication)
        if (amqpMsgId != null && !amqpMsgId.isBlank() && gwinRepository.existsByMessageId(amqpMsgId)) {
            log.warn("AMQP message {} already exists in gwin. Ignoring duplicate.", amqpMsgId);
            return;
        }

        // Kiểm tra quyền hạn người dùng (Authorization)
        if (!authorizationService.isSwimUserAuthorized(amqpMsg)) {
            log.warn("AMQP message {} UNAUTHORIZED - rejected by authorization policy", amqpMsgId);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "[SWIM->AMHS] Unauthorized SWIM message rejected: " + amqpMsgId,
                    "gwin", null);
            conversionService.logSwimToAmhs(amqpMsgId, null, "REJECTED", "unauthorized",
                    "SWIM user not authorized");
            return;
        }

        // 2. Phân giải độ ưu tiên
        Integer rawPriority = null;
        boolean priorityValid = true;
        boolean priorityFound = false;

        // 2.1 Kiểm tra amhs_ats_pri / ats_priority trước
        String atsPriProp = getAppProperty(amqpMsg, "amhs_ats_pri");
        if (atsPriProp == null || atsPriProp.isBlank()) atsPriProp = getAppProperty(amqpMsg, "ats_priority");
        if (atsPriProp != null && !atsPriProp.isBlank()) {
            String trimmed = atsPriProp.trim().toUpperCase();
            if (trimmed.equals("SS") || trimmed.equals("DD") || trimmed.equals("FF") || trimmed.equals("GG") || trimmed.equals("KK")) {
                rawPriority = vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(trimmed);
                priorityFound = true;
            } else {
                priorityValid = false;
                log.warn("AMQP {}: Invalid ATS priority string '{}'", amqpMsgId, atsPriProp);
            }
        }

        // 2.2 AMQP Properties priority
        if (!priorityFound) {
            try {
                String amqpPriStr = getMsgProperty(amqpMsg, "amqpPriority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, "amqp_priority");
                if (amqpPriStr == null || amqpPriStr.isBlank()) amqpPriStr = getMsgProperty(amqpMsg, "priority");
                
                if (amqpPriStr != null && !amqpPriStr.isBlank()) {
                    priorityFound = true;
                    String trimmed = amqpPriStr.trim();
                    if (trimmed.matches("-?\\d+")) {
                        rawPriority = Integer.parseInt(trimmed);
                    } else {
                        priorityValid = false;
                        log.warn("AMQP {}: Priority property is text/non-numeric '{}'", amqpMsgId, amqpPriStr);
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading priority property for {}: {}", amqpMsgId, e.getMessage());
            }
        }

        // 2.3 JMSPriority header fallback
        if (!priorityFound) {
            try {
                int jmsPri = amqpMsg.getJMSPriority();
                if (jmsPri >= 0) {
                    rawPriority = jmsPri;
                    priorityFound = true;
                }
            } catch (Exception ignored) {}
        }

        // 2.4 Range check 0-9
        if (priorityValid && priorityFound && rawPriority != null) {
            if (rawPriority < 0 || rawPriority > 9) {
                priorityValid = false;
                log.warn("AMQP {}: Priority value {} is out of range 0-9", amqpMsgId, rawPriority);
            }
        } else if (!priorityFound) {
            priorityValid = false;
            log.warn("AMQP {}: Mandatory priority field is missing", amqpMsgId);
        }

        int priority = (priorityValid && rawPriority != null) ? rawPriority : 4;
        String atsPriority = priorityValid ? vn.asg.swim.model.AmqpProperties.mapPriorityToAmhs(priority) : null;

        // 3. Trích xuất & kiểm tra creation-time — chỉ từ properties/application-properties tầng AMQP
        boolean creationTimeValid = true;
        String amhsAtsFt = null;
        boolean creationTimeFieldFound = false;

        if (!creationTimeFieldFound) {
            String atsFt = getAppProperty(amqpMsg, "amhs_ats_ft");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, "creation_time");
            if (atsFt == null || atsFt.isBlank()) atsFt = getAppProperty(amqpMsg, "creation-time");

            if (atsFt != null && !atsFt.isBlank()) {
                String trimmed = atsFt.trim();
                // CTSW105 (§4.5.2.10a): chỉ nhận amhs_ats_ft khi đúng date-time group 6 số DDhhmm
                // (hoặc epoch millis - client JMS hay gửi dạng này). "0"/"000000"/"null" là giá trị
                // rỗng trá hình, không phải giờ thật.
                boolean placeholder = "0".equals(trimmed) || "000000".equals(trimmed) || "null".equalsIgnoreCase(trimmed);
                if (!placeholder && trimmed.matches("^\\d{6}$")) {
                    creationTimeFieldFound = true;
                    amhsAtsFt = trimmed;
                } else if (!placeholder) {
                    try {
                        long epochMs = Long.parseLong(trimmed);
                        if (epochMs > 0) {
                            creationTimeFieldFound = true;
                            LocalDateTime dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), java.time.ZoneOffset.UTC);
                            amhsAtsFt = dt.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
                        }
                    } catch (NumberFormatException ignored) {
                        // sai định dạng -> rơi xuống fallback creation-time bên dưới
                    }
                }
                // CTSW105 (§4.5.2.10b): sai định dạng thì DÙNG creation-time của AMQP, không được
                // lấy nguyên chuỗi rác làm filing time.
                if (!creationTimeFieldFound) {
                    log.warn("AMQP {}: amhs_ats_ft '{}' sai định dạng - dùng creation-time của AMQP thay thế",
                            amqpMsgId, trimmed);
                }
            }
        }

        if (!creationTimeFieldFound) {
            try {
                long jmsTimestamp = amqpMsg.getJMSTimestamp();
                if (jmsTimestamp > 0) {
                    creationTimeFieldFound = true;
                    LocalDateTime dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(jmsTimestamp), java.time.ZoneOffset.UTC);
                    amhsAtsFt = dt.format(java.time.format.DateTimeFormatter.ofPattern("ddHHmm"));
                }
            } catch (Exception ignored) {}
        }

        // CTSW102: creation-time là trường bắt buộc — nếu không có property lẫn JMSTimestamp
        // hợp lệ, phải từ chối bản tin, không được tự bịa giờ hiện tại rồi coi là hợp lệ.
        if (!creationTimeFieldFound) {
            creationTimeValid = false;
            log.warn("AMQP {}: Mandatory creation-time field is missing", amqpMsgId);
        }

        // 4. Trích xuất các thuộc tính tiêu chuẩn khác
        // EUR Doc 047 §4.5.2.3: omit subject entirely if neither property is present, không gán placeholder
        String subject = getAppProperty(amqpMsg, "amhs_subject");
        if (subject == null || subject.isBlank()) {
            subject = getMsgProperty(amqpMsg, "subject");
        }
        if (subject != null && subject.length() > 128) {
            subject = subject.substring(0, 128);
        }

        String amhsAtsOhi = getAppProperty(amqpMsg, "amhs_ats_ohi");
        if (amhsAtsOhi != null && !amhsAtsOhi.isBlank()) {
            // EUR Doc 047 v3.0 §4.5.2.11 (d,e): priority < 6 -> 53 ký tự; priority >= 6 -> 48 ký tự
            int maxOhiLen = (priority >= 6) ? 48 : 53;
            if (amhsAtsOhi.length() > maxOhiLen) {
                amhsAtsOhi = amhsAtsOhi.substring(0, maxOhiLen);
            }
        }

        String amhsIpmId = getAppProperty(amqpMsg, "amhs_ipm_id");
        String amhsBodypartType = getAppProperty(amqpMsg, "amhs_bodypart_type");
        String amhsContentEncoding = getAppProperty(amqpMsg, "amhs_content_encoding");
        String amhsMessageSigned = getAppProperty(amqpMsg, "amhs_message_signed");

        String amhsFtbpFileName = getAppProperty(amqpMsg, "amhs_ftbp_file_name");
        String amhsFtbpObjectSize = getAppProperty(amqpMsg, "amhs_ftbp_object_size");
        String amhsFtbpLastMod = getAppProperty(amqpMsg, "amhs_ftbp_last_mod");
        String amhsRegisteredIdentifier = getAppProperty(amqpMsg, "amhs_registered_identifier");
        String amhsUserVisibleString = getAppProperty(amqpMsg, "amhs_user_visible_string");

        // §4.5.2.14: registered-identifier mang OID khác OID mặc định thì
        // user-visible-string BẮT BUỘC phải có. Thiếu -> log + báo Control Position, nhưng vẫn
        // chuyển tiếp bản tin. (Việc đối chiếu OID với bảng đăng ký ở ICAO EUR AMHS Manual,
        // Appendix B A.2.4.2.6 nằm ngoài phạm vi - gateway không có bảng đó.)
        if (amhsRegisteredIdentifier != null && !amhsRegisteredIdentifier.isBlank()
                && !vn.asg.swim.model.AmqpProperties.isDefaultRegisteredIdentifier(amhsRegisteredIdentifier)
                && (amhsUserVisibleString == null || amhsUserVisibleString.isBlank())) {
            log.warn("AMQP {}: amhs_registered_identifier '{}' is non-default OID but missing amhs_user_visible_string",
                    amqpMsgId, amhsRegisteredIdentifier);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "[SWIM->AMHS] AMQP " + amqpMsgId + ": amhs_registered_identifier '" + amhsRegisteredIdentifier
                            + "' is not default OID but missing amhs_user_visible_string (§4.5.2.14)",
                    "gwin", null);
        }
        
        String notificationRequests = null;
        List<String> notifList = getAppPropertyAsList(amqpMsg, "notification_requests");
        if (notifList == null || notifList.isEmpty()) {
            notifList = getAppPropertyAsList(amqpMsg, "notification-requests");
        }
        if (notifList != null && !notifList.isEmpty()) {
            notificationRequests = String.join(",", notifList);
        }

        // CTSW102 (§4.5.1.5/§4.5.1.8): amhs_recipients is the only recipients property the spec
        // recognises - no property-name or routing-rule fallback. Missing/unresolvable/over-max
        // recipients park the message as UNROUTED (not terminal FAILED) per §4.2.6.1's Control
        // Position role - reporting exists "for appropriate action", including recovering a rejection.
        List<String> recipientsList = getAppPropertyAsList(amqpMsg, "amhs_recipients");

        boolean recipientsValid = true;
        if (recipientsList.isEmpty()) {
            recipientsValid = false;
            log.warn("AMQP {}: Mandatory amhs_recipients field is missing or empty", amqpMsgId);
        } else {
            // EUR Doc 047 §3.3.2.4: 0 hoặc không cấu hình = không giới hạn
            int maxRecipients = configService.getMaxMsgRecipients();
            if (maxRecipients > 0 && recipientsList.size() > maxRecipients) {
                recipientsValid = false;
                log.warn("AMQP {}: Recipients count {} exceeds maximum {}", amqpMsgId, recipientsList.size(), maxRecipients);
            }
        }

        // EUR Doc 047 §4.5.2.9: lọc từng recipient theo format AF-address (8 ký tự).
        // Recipient lỗi CHỈ bị loại + báo Control Position, KHÔNG làm cả bản tin bị từ chối,
        // trừ khi TẤT CẢ recipient đều không dịch được (per-recipient tolerant, not all-or-nothing).
        if (recipientsValid) {
            List<String> validRecipients = new ArrayList<>();
            List<String> invalidRecipients = new ArrayList<>();
            for (String recipient : recipientsList) {
                if (validationService.validateAftnAddress(recipient, "Recipient").isValid()) {
                    validRecipients.add(recipient);
                } else {
                    invalidRecipients.add(recipient);
                }
            }
            if (!invalidRecipients.isEmpty()) {
                log.warn("AMQP {}: {} recipient(s) could not be translated to an AF-address: {}",
                        amqpMsgId, invalidRecipients.size(), invalidRecipients);
                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR,
                        GwAlert.SEV_WARNING,
                        "[SWIM->AMHS] AMQP " + amqpMsgId + ": unrecognised recipient(s) dropped: " + invalidRecipients,
                        "gwin", null);
            }
            if (validRecipients.isEmpty()) {
                recipientsValid = false;
                log.warn("AMQP {}: none of the recipients could be translated to an AF-address", amqpMsgId);
            } else {
                recipientsList = validRecipients;
            }
        }

        String amhsRecipients = String.join(" ", recipientsList);

        // CTSW109 (EUR Doc 047 §4.5.2.12): chỉ property amhs_originator được spec công nhận cho
        // originator. Nếu thiếu hoặc không đúng addressee indicator 8 ký tự, dùng default
        // originator, đồng thời logged VÀ reported to Control Position (§4.5.2.12b) — không suy
        // luận originator từ property tên khác hay routing rule (cùng lý do với CTSW102).
        String amhsOriginator = getAppProperty(amqpMsg, "amhs_originator");
        if (amhsOriginator == null || amhsOriginator.isBlank()
                || !validationService.validateAftnAddress(amhsOriginator, "amhs_originator").isValid()) {
            String invalidOriginator = amhsOriginator;
            amhsOriginator = configService.getDefaultOriginator();
            log.warn("AMQP {}: Originator '{}' is unknown or invalid. Using default originator: {}",
                    amqpMsgId, invalidOriginator, amhsOriginator);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_WARNING,
                    "[SWIM->AMHS] AMQP " + amqpMsgId + ": invalid/unknown amhs_originator '" + invalidOriginator
                            + "', falling back to default originator " + amhsOriginator,
                    "gwin", null);
        }

        // amhs_originator/amhs_recipients are always sourced directly from AMQP properties now
        // (see CTSW102/CTSW109 above) — the addressing source label just reflects whether a usable
        // recipients list was actually present.
        ResolvedAddressing resolved = new ResolvedAddressing(amhsOriginator, amhsRecipients,
                recipientsValid ? ResolvedAddressing.SOURCE_AMQP_PROPERTY : ResolvedAddressing.SOURCE_UNRESOLVED);

        // CTSW103 (§3.3.3 / §4.5.2.10.1 / §4.5.3.7-9): phân giải ATSMHS service level TRƯỚC khi
        // đóng gói properties, vì mức dịch vụ quyết định cách thành phần dựng IPM map các trường
        // (basic: ats_ft -> ATS-message-Filing-Time, ohi -> ATS-message-Optional-Heading-Info;
        // extended: ats_ft -> authorization-time, ohi -> originators-reference,
        // precedence-policy-identifier). Giá trị phải được lưu lại, không chỉ ghi log.
        String atsmhsOverrideMode = getAppProperty(amqpMsg, "atsmhs_service_level");
        if (atsmhsOverrideMode == null || atsmhsOverrideMode.isBlank()) {
            atsmhsOverrideMode = getAppProperty(amqpMsg, "atsmhs-service-level");
        }
        String atsmhsServiceLevel = atsmhsResolver.resolve(atsmhsOverrideMode, contentType, amhsRecipients);

        // Chuyển đổi các thuộc tính ứng dụng sang định dạng JSON
        java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
        if (atsPriority != null)
            props.put("ats_priority", atsPriority);
        if (amhsAtsFt != null)
            props.put("amhs_ats_ft", amhsAtsFt);
        if (amhsAtsOhi != null)
            props.put("amhs_ats_ohi", amhsAtsOhi);
        if (amhsIpmId != null)
            props.put("amhs_ipm_id", amhsIpmId);
        if (amhsBodypartType != null)
            props.put("amhs_bodypart_type", amhsBodypartType);
        if (amhsContentEncoding != null)
            props.put("amhs_content_encoding", amhsContentEncoding);
        if (amhsMessageSigned != null)
            props.put("amhs_message_signed", amhsMessageSigned);
        if (amhsFtbpFileName != null)
            props.put("amhs_ftbp_file_name", amhsFtbpFileName);
        if (amhsFtbpObjectSize != null)
            props.put("amhs_ftbp_object_size", amhsFtbpObjectSize);
        if (amhsFtbpLastMod != null)
            props.put("amhs_ftbp_last_mod", amhsFtbpLastMod);
        if (amhsRegisteredIdentifier != null)
            props.put("amhs_registered_identifier", amhsRegisteredIdentifier);
        if (amhsUserVisibleString != null)
            props.put("amhs_user_visible_string", amhsUserVisibleString);
        if (notificationRequests != null)
            props.put("notification_requests", notificationRequests);
        if (contentType != null)
            props.put("content_type", contentType);
        if (subject != null)
            props.put("subject", subject);

        String tempJson = "{}";
        try {
            tempJson = objectMapper.writeValueAsString(props);
        } catch (Exception e) {
            log.error("Failed to serialize AMQP properties to JSON for message {}: {}", amqpMsgId, e.getMessage());
        }
        final String amqpPropertiesJson = tempJson;

        // Kiểm tra tính hợp lệ bản tin theo EUR Doc 047
        // CTSW111: đo kích thước payload AMQP gốc (raw bytes), không phải độ dài chuỗi base64
        // đã encode cho nội dung binary.
        int payloadByteSize = binaryPayload != null
                ? binaryPayload.length
                : (finalContent != null ? finalContent.getBytes(StandardCharsets.UTF_8).length : 0);
        MessageValidationService.ValidationResult validationResult = validationService.validateSwimToAmhs(
                amqpMsgId != null ? amqpMsgId : "",
                amqpMsg,
                finalContent,
                payloadByteSize);

        // CTSW102 (§4.5.1.5): amhs_recipients is mandatory for AMHS-unaware service level.
        // If missing or all recipients invalid/over max -> reject immediately.
        boolean isCompliant = hasMessageId && priorityValid && creationTimeValid && dataValid && contentTypeSupported && recipientsValid && validationResult.isValid();

        // NẾU KHÔNG THỎA MÃN 1 TRONG CÁC ĐIỀU KIỆN, VẪN LƯU VÀO GWIN VỚI STATUS = 4 (IN_FAILED)
        if (!isCompliant) {
            List<String> errors = new ArrayList<>();
            if (!hasMessageId) errors.add("Missing messageId");
            if (!priorityValid) errors.add("Invalid priority: " + rawPriority + " (must be 0-9)");
            if (!creationTimeValid) errors.add("Mandatory field 'creation-time' is missing or invalid");
            if (!dataValid) errors.add("Mandatory field 'data/amqp-value' is missing or empty");
            if (!contentTypeSupported) errors.add(contentTypeError != null ? contentTypeError
                    : "Unsupported content-type: " + contentType);
            if (!recipientsValid) errors.add("Mandatory field 'amhs_recipients' is missing or invalid");
            if (!validationResult.isValid()) errors.addAll(validationResult.getErrors());

            String errorMessage = String.join("; ", errors);
            log.error("AMQP message {} validation FAILED: {}", amqpMsgId, errorMessage);

            conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED", "validation-failed",
                    errorMessage);

            Gwin failedGwin = new Gwin();
            failedGwin.setMessageId(amqpMsgId);
            failedGwin.setSource(queue);
            failedGwin.setSubject(subject);
            failedGwin.setAmhsRecipients(amhsRecipients);
            failedGwin.setAmqpProperties(amqpPropertiesJson);
            failedGwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
            failedGwin.setTime(LocalDateTime.now());
            failedGwin.setPayloadContent(finalContent);
            String bodyType = deriveBodyType(amhsBodypartType, contentType);
            failedGwin.setBodyType(bodyType);
            failedGwin.setContentType(contentType);
            failedGwin.setOrigin(resolved.originator());
            failedGwin.setAddress(resolved.recipients());
            failedGwin.setAddressingSource(resolved.source());
            failedGwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
            failedGwin.setRejectionReason("validation-failed");
            failedGwin.setRejectionSource("SWIM");
            failedGwin.setRejectionDiagnostic(errorMessage);
            failedGwin.setStatus(InboundStatus.FAILED.getValue()); // status = 1 (FAILED)

            Long savedMsgid = null;
            try {
                Gwin saved = gwinRepository.save(failedGwin);
                savedMsgid = saved.getMsgid();
            } catch (DataIntegrityViolationException e) {
                log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
            }

            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR,
                    GwAlert.SEV_ERROR,
                    "[SWIM->AMHS] Message validation failed: " + amqpMsgId + " - " + errorMessage,
                    "gwin", savedMsgid);
            return;
        }

        // Kiểm tra cấp độ dịch vụ ATSMHS theo đặc tả (mức dịch vụ đã được phân giải ở trên)
        if (resolved.isResolved()) {
            // §3.3.3.2 / §4.5.1.6(a): "co noi dung binary hay khong" la thuoc tinh KHAI BAO cua ban
            // tin, khong phai ket qua doan byte. Suy ra tu content-type truoc, binaryPayload chi la
            // duong bo sung cho ban tin thieu content-type ro rang.
            boolean hasBinaryContent = declaredBinary || binaryPayload != null;

            // CTSW103 bản tin 2: chế độ BASIC không hỗ trợ nội dung nhị phân (binary)
            if (!atsmhsResolver.validateContent(atsmhsServiceLevel, contentType, hasBinaryContent)) {
                String errorDiagnostic = "Binary content not supported in BASIC mode";
                log.error("AMQP message {} REJECTED: BASIC ATSMHS mode cannot handle binary content", amqpMsgId);

                conversionService.logSwimToAmhs(amqpMsgId, resolved.originator(), "REJECTED",
                        "atsmhs-validation-failed", errorDiagnostic);

                Gwin failedGwin = new Gwin();
                failedGwin.setMessageId(amqpMsgId);
                failedGwin.setSource(queue);
                failedGwin.setSubject(subject);
                failedGwin.setAmhsRecipients(amhsRecipients);
                failedGwin.setAmqpProperties(amqpPropertiesJson);
                failedGwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
                failedGwin.setTime(LocalDateTime.now());
                failedGwin.setPayloadContent(finalContent);
                String bodyType = deriveBodyType(amhsBodypartType, contentType);
                failedGwin.setBodyType(bodyType);
                failedGwin.setContentType(contentType);
                failedGwin.setOrigin(resolved.originator());
                failedGwin.setAddress(resolved.recipients());
                failedGwin.setAddressingSource(resolved.source());
                failedGwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
                failedGwin.setRejectionReason("atsmhs-validation-failed");
                failedGwin.setRejectionSource("SWIM");
                failedGwin.setRejectionDiagnostic(errorDiagnostic);
                failedGwin.setStatus(InboundStatus.FAILED.getValue()); // status = 1 (FAILED)

                Long savedMsgid = null;
                try {
                    Gwin saved = gwinRepository.save(failedGwin);
                    savedMsgid = saved.getMsgid();
                } catch (DataIntegrityViolationException e) {
                    log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
                }

                alertService.create(
                        GwAlert.TYPE_VALIDATION_ERROR,
                        GwAlert.SEV_ERROR,
                        "[SWIM->AMHS] Binary content rejected in BASIC ATSMHS mode: " + amqpMsgId,
                        "gwin", savedMsgid);
                return;
            }

            log.debug("AMQP {}: ATSMHS service level = {}", amqpMsgId, atsmhsServiceLevel);
        }

        Gwin gwin = new Gwin();
        gwin.setMessageId(amqpMsgId);
        gwin.setSource(queue);
        gwin.setSubject(subject);
        gwin.setAmhsRecipients(amhsRecipients);
        gwin.setAmqpProperties(amqpPropertiesJson);
        gwin.setPriority((byte) Math.min(Math.max(priority, 0), 9));
        gwin.setTime(LocalDateTime.now());
        gwin.setPayloadContent(finalContent);
        String bodyType = deriveBodyType(amhsBodypartType, contentType);
        gwin.setBodyType(bodyType);
        gwin.setContentType(contentType);
        if (resolved != null) {
            gwin.setOrigin(resolved.originator());
            gwin.setAddress(resolved.recipients());
            gwin.setAddressingSource(resolved.source());
            gwin.setAtsmhsServiceLevel(atsmhsServiceLevel);
        }

        try {
            // Giữ nguyên nội dung bản tin gốc, không convert theo chiều nào (theo ICAO Doc 047)
            gwin.setStatus(resolved != null && resolved.isResolved() ? InboundStatus.PENDING.getValue() : InboundStatus.UNROUTED.getValue());

            try {
                gwinRepository.save(gwin);
            } catch (DataIntegrityViolationException e) {
                log.warn("AMQP message {} already exists (race condition). Ignoring.", amqpMsgId);
                return;
            }

            String actionTag = "received-" + (resolved != null ? resolved.source().toLowerCase().replaceAll("[^a-z0-9]", "_") : "unresolved");
            conversionService.logSwimToAmhs(amqpMsgId, resolved != null ? resolved.originator() : null,
                    gwin.getStatus().equals(InboundStatus.PENDING.getValue()) ? "OK" : "UNROUTED",
                    actionTag,
                    (resolved != null && resolved.isResolved()) ? null : "MISSING_AMHS_RECIPIENTS",
                    amhsIpmId,
                    gwin.getPayloadContent());


        } catch (Exception e) {
            log.error("AMQP {} Fatal Error: {}", amqpMsgId, e.getMessage());
        }
    }

    /**
     * Dừng kết nối và đóng toàn bộ session active.
     */
    public synchronized void stopAll() {
        running.set(false);
        activeSessions.forEach(s -> {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        });
        activeSessions.clear();
    }

    /**
     * Dọn dẹp tài nguyên khi bean bị hủy.
     */
    @PreDestroy
    public void destroy() {
        stopAll();
    }

    /**
     * EUR Doc 047 §4.5.2.4: khi amhs_bodypart_type có mặt (AMHS-aware), dùng trực
     * tiếp; nếu vắng mặt (AMHS-unaware), suy luận từ content-type (b): octet-stream
     * -> file-transfer-body-part, còn lại -> general-text-body-part.
     */
    private String deriveBodyType(String amhsBodypartType, String contentType) {
        if (amhsBodypartType != null) {
            return "file-transfer-body-part".equalsIgnoreCase(amhsBodypartType) ? "ftbp" : "text";
        }
        if (contentType != null && contentType.toLowerCase().contains("octet-stream")) {
            return "ftbp";
        }
        return "text";
    }
    private String cleanAmqpMessageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        String[] qpidPrefixes = {
            "ID:AMQP_NO_PREFIX:",
            "ID:AMQP_STRING:",
            "ID:AMQP_BINARY:",
            "ID:AMQP_ULONG:",
            "ID:AMQP_UUID:",
            "ID:"
        };
        for (String prefix : qpidPrefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break;
            }
        }
        if (cleaned.isBlank() || "null".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
    }

    private String safeGetStringProperty(Message msg, String key) {
        if (key == null || key.isBlank()) return null;
        try {
            String val = msg.getStringProperty(key);
            if (val != null) return val;
        } catch (Exception ignored) {}

        String altKey = key.contains("-") ? key.replace("-", "_") : key.replace("_", "-");
        if (!altKey.equals(key)) {
            try {
                return msg.getStringProperty(altKey);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String getMsgProperty(Message msg, String key) {
        if ("content-type".equals(key) || "content_type".equals(key)) {
            try {
                String ct = msg.getStringProperty("JMS_AMQP_CONTENT_TYPE");
                if (ct != null) return ct;
            } catch (Exception ignored) {}
        }
        return safeGetStringProperty(msg, key);
    }

    private String getAppProperty(Message msg, String key) {
        return safeGetStringProperty(msg, key);
    }

    private List<String> getAppPropertyAsList(Message msg, String key) {
        List<String> list = new ArrayList<>();
        String val = safeGetStringProperty(msg, key);
        if (val != null && !val.isBlank()) {
            String[] parts = val.trim().split("[,\\s]+");
            for (String part : parts) {
                if (!part.isBlank()) list.add(part);
            }
        }
        return list;
    }

    private byte[] decompressGzip(byte[] compressed) {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressed);
             java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bais);
             java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to decompress GZIP data: {}", e.getMessage());
            return compressed;
        }
    }

    /**
     * Nhận biết dữ liệu văn bản.
     */
    /**
     * Decode UTF-8 nghiêm ngặt - báo lỗi CharacterCodingException nếu bytes chứa
     * chuỗi byte không hợp lệ UTF-8, thay vì âm thầm thay bằng ký tự U+FFFD như
     * {@code new String(bytes, UTF_8)}. Dùng để phát hiện content-type/content mismatch thật.
     */
    private String strictUtf8Decode(byte[] bytes) throws java.nio.charset.CharacterCodingException {
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

    private boolean isProbablyText(String s) {
        if (s == null || s.isEmpty())
            return false;
        long controlChars = s.chars()
                .filter(c -> c < 32 && c != '\t' && c != '\n' && c != '\r')
                .count();
        return controlChars < s.length() * 0.05;
    }

    /**
     * Định kỳ kiểm tra sự thay đổi cấu hình định tuyến inbound để re-subscribe.
     */
    @Scheduled(fixedDelay = 10000)
    public void checkRoutingChanges() {
        if (!connectionManager.getConnected().get()) {
            return;
        }
        try {
            List<String> activeQueues = routingService.getActiveInboundTopics();
            if (!running.get() || !activeQueues.equals(currentSubscribedQueues) || activeSessions.isEmpty()) {
                log.info("Re-subscribing inbound topics (running={}, activeQueues={}, activeSessions={})...", 
                        running.get(), activeQueues, activeSessions.size());
                subscribeAll();
            }
        } catch (Exception e) {
            log.error("Error checking routing changes for re-subscription: {}", e.getMessage());
        }
    }
}
