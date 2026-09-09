package vn.asg.swim.service;

import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.model.AmqpProperties;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.util.AmqpMessageIdUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Xử lý tiến trình gửi tin đi từ hàng đợi gwout_dispatch (chiều AMHS sang
 * SWIM).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundDispatchService {

    /** Abstract-value X.400 của content-type duy nhất được chấp nhận (EUR Doc 047 §4.4.1.1). */
    private static final int IPM_1988_CONTENT_TYPE = 22;

    private final ConnectionManagerService connectionManager;
    private final RoutingService routingService;
    private final MessageConversionService conversionService;
    private final MessageValidationService validationService;
    private final AuthorizationService authorizationService;
    private final ConfigService configService;
    private final AlertService alertService;
    private final GwoutDispatchRepository gwoutDispatchRepository;
    private final GwoutRepository gwoutRepository;

    /**
     * Từ chối bản tin cho TOÀN BỘ recipient: cập nhật trạng thái, ghi traffic log và báo
     * Control Position.
     * <p>
     * ITCU KHÔNG sinh AMHS report ở đây. Việc phát DR/NDR ra đường truyền X.400 cho chiều
     * AMHS → SWIM do AMHS Component đảm nhiệm trọn vẹn - đã xác nhận report về tới giao diện
     * AMHS. Tiêu chí chấm của Appendix A cho các test case này ("the IUT returns a DR/NDR")
     * kiểm tra ở giao diện AMHS, và không test case nào trong nhóm đó đặt yêu cầu với
     * Control Position.
     * <p>
     * Bộ ba (reason-code, diagnostic-code, supplementary-information) vẫn được ghi vào
     * {@code gwout} và traffic log để Control Position tra cứu được bản tin bị từ chối.
     *
     * @param alertMessage nội dung cảnh báo gửi Control Position, null nếu nhánh này không báo
     */
    private void rejectMessage(Gwout gwout, String rejectionReason, String diagnosticCode,
            String supplementaryInfo, String actionTaken, String alertMessage) {
        if (alertMessage != null) {
            alertService.create(GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    alertMessage, "gwout", gwout.getMsgid());
        }
        gwout.setStatus(OutboundStatus.FAILED.getValue());
        gwout.setRejectionReason(rejectionReason);
        gwout.setRejectionDiagnostic(diagnosticCode);
        gwout.setRejectionSource("AMHS");
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwimRejected(gwout, actionTaken, diagnosticCode, supplementaryInfo);
    }

    /**
     * Ghi traffic log cho MỘT recipient không chuyển giao được, bản tin vẫn đi tới các recipient
     * còn lại.
     * <p>
     * Doc 9880 §4.5.2.4.11 dùng chung non-delivery-diagnostic-code {@code unrecognised-OR-name}
     * cho cả địa chỉ sai khuôn lẫn địa chỉ không tra được đích publish; {@code actionTaken} phân
     * biệt hai nguyên nhân trong traffic log để Control Position truy được.
     */
    private void recordRecipientNdr(Gwout gwout, String recipient, String actionTaken) {
        final String supplementary = "unable to convert to AMQP due to unrecognized recipient O/R address";
        conversionService.logAmhsToSwimRejected(gwout,
                actionTaken + ": " + recipient, "unrecognised-OR-name", supplementary);
    }

    /**
     * Thực hiện kiểm tra, phân tích bản tin AMHS và chuyển tiếp nguyên văn sang SWIM.
     * Trạng thái sau khi hoàn tất sẽ được đặt thành TRANSFORMED (2) hoặc FAILED
     * (5).
     */
    @Transactional
    public void processOutboundMessage(Gwout gwout) {
        String origin = gwout.getOrigin();
        if (!MessageValidationService.isValidAftnAddress(origin)) {
            log.warn("gwout#{} rejected: origin '{}' is invalid (must be 8 uppercase alphabetic characters, no digits, no spaces)",
                    gwout.getMsgid(), origin);
            rejectMessage(gwout, "invalid-origin-format", "invalid-arguments",
                    "unable to convert to AMQP due to unrecognized originator O/R address",
                    "invalid_origin_format",
                    "gwout#" + gwout.getMsgid() + " rejected: originator O/R address '" + origin
                            + "' không hợp lệ");
            return;
        }

        // CTSW011 - CTSW013: Phát hiện bản tin Probe từ AMHS
        boolean isProbe = "probe".equalsIgnoreCase(gwout.getBodyType()) || "PROBE".equalsIgnoreCase(gwout.getText());
        if (isProbe) {
            log.info("Processing AMHS Probe for gwout#{}", gwout.getMsgid());
            processAmhsProbe(gwout);
            return;
        }

        // CTSW008 (§4.4.1.1): content-type của Message Transfer Envelope phải là
        // interpersonal-messaging-1988(22). Mọi giá trị khác - kể cả
        // interpersonal-messaging-1984(2), edi-messaging(35), unidentified(0) - phải bị từ chối.
        // NULL = bản tin cũ không có dữ liệu content-type -> bỏ qua bước kiểm tra.
        Integer x400ContentType = gwout.getX400ContentType();
        if (x400ContentType != null && x400ContentType != IPM_1988_CONTENT_TYPE) {
            log.warn("gwout#{} rejected: content-type {} khác interpersonal-messaging-1988(22)",
                    gwout.getMsgid(), x400ContentType);
            // CTSW008 chỉ yêu cầu non-delivery-reason-code + non-delivery-diagnostic-code.
            rejectMessage(gwout, "unsupported-content-type", "content-type-not-supported", null,
                    "unsupported_content_type: " + x400ContentType,
                    "gwout#" + gwout.getMsgid() + " rejected: unsupported content-type " + x400ContentType);
            return;
        }

        // CTSW006 (§4.4.2.6): so sánh với "Maximum message data size" phải dùng kích thước THẬT
        // của payload. Với file-transfer-body-part, gwout.text chứa dữ liệu đã base64-encode nên
        // dài hơn dữ liệu gốc khoảng 33% - phải giải mã trước khi đo.
        MessageValidationService.ValidationResult dirResult = validationService.validateAmhsToSwim(gwout.getText(),
                gwout.getAddress(), payloadByteSize(gwout));
        if (!dirResult.isValid()) {
            log.warn("gwout#{} rejected by validation: {}", gwout.getMsgid(), dirResult.getErrorMessage());
            rejectMessage(gwout, "validation-failed",
                    ndrDiagnosticFor(dirResult.getErrorMessage()),
                    ndrSupplementaryFor(dirResult.getErrorMessage()),
                    "validation_failed: " + dirResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + dirResult.getErrorMessage());
            return;
        }

        // CTSW004 (§4.4.2.5): ATS-message-header phải có priority và filing-time hợp lệ,
        // nếu sai cú pháp thì từ chối và sinh NDR content-syntax-error.
        MessageValidationService.ValidationResult headerResult =
                validationService.validateAtsMessageHeader(gwout.getAmhsPriority(), gwout.getFilingTime());
        if (!headerResult.isValid()) {
            log.warn("gwout#{} rejected: ATS-message-header sai cú pháp - {}",
                    gwout.getMsgid(), headerResult.getErrorMessage());
            rejectMessage(gwout, "ats-header-syntax-error", "content-syntax-error",
                    "unable to convert to AMQP due to ATS-message-header or Heading Fields syntax error",
                    "ats_header_syntax_error: " + headerResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: ATS-message-header syntax error - "
                            + headerResult.getErrorMessage());
            return;
        }

        if (!authorizationService.isAmhsUserAuthorized(gwout.getOrigin())) {
            log.warn("gwout#{} REJECTED: AMHS originator '{}' not authorized", gwout.getMsgid(), gwout.getOrigin());
            // §4.4.8: ITCU không chuyển giao được thì người gửi X.400 phải nhận NDR. Trước đây
            // nhánh này chỉ đặt status=FAILED nên bản tin biến mất khỏi cả hai phía mà bên gửi
            // không nhận được gì.
            rejectMessage(gwout, "unauthorized-originator", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized originator O/R address",
                    "unauthorized_originator: " + gwout.getOrigin(),
                    "Unauthorized AMHS originator: " + gwout.getOrigin()
                            + " (gwout#" + gwout.getMsgid() + ")");
            return;
        }

        // CTSW016 (§4.4.2.1): kiểm current encoded-information-types TRƯỚC các bước sau.
        MessageValidationService.ValidationResult eitTypeResult =
                validationService.validateEncodedInformationTypes(gwout.getOriginEit());
        if (!eitTypeResult.isValid()) {
            log.warn("gwout#{} rejected by EIT check: {}", gwout.getMsgid(), eitTypeResult.getErrorMessage());
            rejectMessage(gwout, "unsupported-eit", "encoded-information-types-unsupported", null,
                    "unsupported_eit: " + eitTypeResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + eitTypeResult.getErrorMessage());
            return;
        }

        // Chuẩn hoá mã số thô của bodyPartType TRƯỚC mọi bước so sánh bên dưới.
        // AmhsToGwoutSyncScheduler đã chuẩn hoá cho bản tin đi qua đường đồng bộ mtcu_tmp, nhưng
        // gwout còn có nguồn khác (amss ghi thẳng vào bảng cho Probe - xem gwout.body_type/
        // content_length). Nếu để nguyên mã "403", phép so sánh với "file-transfer-body-part" ở
        // bước đếm body part sẽ không khớp và cặp text+FTBP hợp lệ của CTSW007 bị từ chối nhầm.
        if (gwout.getBodyPartType() != null) {
            String rawType = gwout.getBodyPartType().trim();
            if ("401".equals(rawType)) {
                gwout.setBodyPartType("ia5-text-body-part");
            } else if ("402".equals(rawType)) {
                gwout.setBodyPartType("general-text-body-part");
            } else if ("403".equals(rawType)) {
                gwout.setBodyPartType("file-transfer-body-part");
            }
        }

        // CTSW007 (§4.4.2.2 / §4.4.2.4): kiểm tra số lượng body part của IPM gốc.
        //   1 body part  -> xử lý bình thường
        //   2 body part  -> chỉ hợp lệ khi là cặp text + file-transfer-body-part (§4.4.2.4a)
        //   > 2body part -> từ chối (§4.4.2.2c)
        Integer bodyPartCount = gwout.getNumberOfAttachment();
        if (bodyPartCount != null && bodyPartCount > 1) {
            String supplementary = null;
            if (bodyPartCount > 2) {
                supplementary = "unable to convert to AMQP due to multiple body parts";
            } else if (!"file-transfer-body-part".equals(gwout.getBodyPartType())) {
                // Đúng 2 body part nhưng không có FTBP -> không phải cặp text+FTBP hợp lệ.
                // Appendix A/CTSW007 (bản tin thứ 3) quy định chuỗi supplementary-information
                // là "unsupported body part type"; §4.4.2.4 của EUR Doc 047 dùng chữ
                // "unsupported combination of body part types". Theo tài liệu kiểm thử.
                supplementary = "unable to convert to AMQP due to unsupported body part type";
            }
            if (supplementary != null) {
                log.warn("gwout#{} rejected: {} body part(s), type={}",
                        gwout.getMsgid(), bodyPartCount, gwout.getBodyPartType());
                rejectMessage(gwout, "unsupported-body-parts", "content-syntax-error", supplementary,
                        "unsupported_body_parts: " + bodyPartCount + " parts",
                        "gwout#" + gwout.getMsgid() + " rejected: " + supplementary);
                return;
            }
        }

        // CTSW016: Kiểm thử EIT/Body Part Type của bản tin đi (giá trị đã được chuẩn hoá ở trên)
        if (gwout.getBodyPartType() != null) {
            MessageValidationService.ValidationResult eitResult = validationService.validateBodyPartType(gwout.getBodyPartType());
            if (!eitResult.isValid()) {
                log.warn("gwout#{} rejected by EIT validation: {}", gwout.getMsgid(), eitResult.getErrorMessage());
                rejectMessage(gwout, "unsupported-eit", "content-syntax-error",
                        "unable to convert to AMQP due to unsupported body part type",
                        "unsupported_eit: " + eitResult.getErrorMessage(),
                        "gwout#" + gwout.getMsgid() + " rejected: " + eitResult.getErrorMessage());
                return;
            }
        }

        // CTSW017 (§4.4.2.3): ia5-text-body-part có repertoire ita2 không nằm trong Table 6 -> từ chối.
        // CTSW019 (§4.4.2.3): general-text-body-part có repertoire khác ISO 646 -> theo chính sách
        // nội bộ của AMHS Management Domain (cấu hình ALLOW_NON_ISO646_REPERTOIRE).
        MessageValidationService.ValidationResult repertoireResult =
                validationService.validateRepertoire(gwout.getBodyPartType(), gwout.getBodyPartCharset());
        if (!repertoireResult.isValid()) {
            String supplementary = repertoireResult.getErrorMessage().contains("unsupported-body-part-type")
                    ? "unable to convert to AMQP due to unsupported body part type"
                    : "unable to convert to AMQP due to unsupported encoded-information-types";
            log.warn("gwout#{} rejected by repertoire check: {}", gwout.getMsgid(), repertoireResult.getErrorMessage());
            rejectMessage(gwout, "unsupported-repertoire", "content-syntax-error", supplementary,
                    "unsupported_repertoire: " + repertoireResult.getErrorMessage(),
                    "gwout#" + gwout.getMsgid() + " rejected: " + repertoireResult.getErrorMessage());
            return;
        }

        // CTSW005: Generate NDR if current time exceeds latest delivery time (amhsTtl)
        if (gwout.getAmhsTtl() != null && gwout.getAmhsTtl().isBefore(LocalDateTime.now())) {
            log.warn("gwout#{} TTL expired (latest-delivery-time exceeded)", gwout.getMsgid());
            // CTSW005 chỉ yêu cầu non-delivery-reason-code + non-delivery-diagnostic-code,
            // không yêu cầu supplementary-information.
            rejectMessage(gwout, "ttl-expired", "maximum-time-expired", null, "ttl_expired",
                    "gwout#" + gwout.getMsgid() + " rejected: latest-delivery-time exceeded ("
                            + gwout.getAmhsTtl() + ")");
            return;
        }

        // CTSW020 (§4.4.4.4): phải log và báo Control Position khi recipient có responsibility
        // "responsible" VÀ một trong hai điều kiện sau, NHƯNG bản tin vẫn được chuyển sang SWIM:
        //   - Extended IPM: precedence cao nhất của recipient-extensions bằng 107, hoặc
        //   - Basic IPM: priority-indicator bằng "SS".
        // gwout.address đã được AmhsToGwoutSyncScheduler lọc chỉ còn recipient "responsible"
        // khi AMHS Component cung cấp cột mtcu_to.responsibility (§4.4.3.4.4).
        Integer precedence = gwout.getPrecedence();
        boolean ssByPrecedence = precedence != null && precedence == AmqpProperties.PRECEDENCE_SS;
        boolean ssByPriority = precedence == null && "SS".equalsIgnoreCase(gwout.getAmhsPriority());
        if (ssByPrecedence || ssByPriority) {
            String basis = ssByPrecedence ? "precedence 107" : "ATS-message-priority SS";
            log.warn("gwout#{}: bản tin ưu tiên cao nhất ({}) - báo Control Position (§4.4.4.4)",
                    gwout.getMsgid(), basis);
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + ": bản tin AMHS ưu tiên cao nhất (" + basis
                            + ") gửi tới " + gwout.getAddress()
                            + " - cần Control Position xử lý (§4.4.4.4)",
                    "gwout", gwout.getMsgid());
        }

        // Giữ nguyên nội dung bản tin gốc, không convert theo chiều nào (theo ICAO Doc 047)
        try {
            gwout.setStatus(OutboundStatus.TRANSFORMED.getValue());
            if (gwout.getAmhsPriority() != null) {
                gwout.setSwimPriority(vn.asg.swim.model.AmqpProperties.mapAtsPriorityToAmqp(gwout.getAmhsPriority()));
            }
            gwoutRepository.save(gwout);
            conversionService.logAmhsToSwim(gwout, null, "OK", "forwarded_unchanged");
            log.info("gwout#{} forwarded unchanged -> status=OUT_TRANSFORMED", gwout.getMsgid());

            // CTSW003: per-recipient-indicators có yêu cầu Delivery Report. Cờ được
            // AmhsToGwoutSyncScheduler tính sẵn từ mtcu_to.reportRequest/mtaReportRequest.
            // Việc PHÁT DR ra đường truyền X.400 do AMHS Component đảm nhiệm; ở đây chỉ ghi
            // traffic log để Control Position tra cứu được.
            if (Boolean.TRUE.equals(gwout.getAmhsDeliveryReport())) {
                log.info("gwout#{} có yêu cầu Delivery Report (CTSW003) - AMHS Component phát DR",
                        gwout.getMsgid());
                conversionService.logAmhsToSwim(gwout, null, "OK", "dr_requested");
            }
        } catch (Exception e) {
            log.error("gwout#{} processing failed: {}", gwout.getMsgid(), e.getMessage());
            // Không gán non-delivery-diagnostic-code: lỗi nội bộ của ITCU không tương ứng với
            // giá trị nào trong bảng liệt kê của Doc 9880 §4.5.2.4.11.
            rejectMessage(gwout, "processing-failed", null, null,
                    "processing_failed: " + e.getMessage(),
                    "gwout#" + gwout.getMsgid() + " xử lý thất bại: " + e.getMessage());
        }
    }

    /**
     * Xử lý bản ghi phân phối tin đi từ hàng đợi (chỉ thực hiện publish dữ liệu đã
     * chuyển tiếp).
     */
    @Transactional
    public void processDispatch(GwoutDispatch dispatch) {
        Gwout gwout = gwoutRepository.findById(dispatch.getGwoutId()).orElse(null);
        if (gwout == null) {
            handleFailure(dispatch, GwoutDispatch.STEP_ROUTING,
                    new RuntimeException("gwout#" + dispatch.getGwoutId() + " not found"));
            return;
        }

        // Đọc nội dung bản tin gốc (không convert theo chiều nào, theo ICAO Doc 047)
        String payloadContent = gwout.getText();
        if (payloadContent == null || payloadContent.isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("text is empty for gwout#" + gwout.getMsgid()));
            return;
        }

        if (dispatch.getTopic() == null || dispatch.getTopic().isBlank()) {
            handleFailure(dispatch, GwoutDispatch.STEP_PUBLISH,
                    new RuntimeException("publish topic is empty/null for dispatch#" + dispatch.getId()));
            return;
        }

        // EUR Doc 047 §4.4.3.4.4: 1 IPM AMHS chỉ sinh ra 1 message AMQP duy nhất.
        // Gộp mọi dispatch cùng gwout + cùng topic còn sẵn sàng xử lý vào 1 lần publish,
        // amhs_recipients chứa danh sách đầy đủ (phân cách dấu phẩy) thay vì gửi trùng N lần.
        LocalDateTime now = LocalDateTime.now();
        List<GwoutDispatch> group = gwoutDispatchRepository.findByGwoutId(gwout.getMsgid()).stream()
                .filter(d -> dispatch.getTopic().equals(d.getTopic()))
                .filter(d -> GwoutDispatch.STATUS_PENDING.equals(d.getStatus())
                        || GwoutDispatch.STATUS_FAILED.equals(d.getStatus()))
                .filter(d -> d.getNextRetryAt() == null || !d.getNextRetryAt().isAfter(now))
                .toList();

        boolean stillEligible = group.stream()
                .anyMatch(d -> java.util.Objects.equals(d.getId(), dispatch.getId()));
        if (!stillEligible) {
            return; // Đã được publish gộp bởi 1 dispatch anh em khác trong cùng batch
        }

        String combinedRecipients = group.stream()
                .map(GwoutDispatch::getRecipient)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));

        for (GwoutDispatch d : group) {
            d.setStatus(GwoutDispatch.STATUS_PUBLISHING);
            gwoutDispatchRepository.save(d);
        }

        try {
            String amqpMessageId = publish(gwout, dispatch.getTopic(), combinedRecipients, payloadContent, gwout.getContentType());
            gwout.setAmqpMessageId(amqpMessageId);
            gwout.setMessageSigned("unsigned");
            gwoutRepository.save(gwout);

            LocalDateTime sentAt = LocalDateTime.now();
            for (GwoutDispatch d : group) {
                d.setStatus(GwoutDispatch.STATUS_SENT);
                d.setSentAt(sentAt);
                gwoutDispatchRepository.save(d);
            }
            log.info("gwout#{} -> {} dispatch(es) merged into 1 AMQP publish -> topic={}, recipients={}",
                    gwout.getMsgid(), group.size(), dispatch.getTopic(), combinedRecipients);

        } catch (Exception e) {
            for (GwoutDispatch d : group) {
                handleFailure(d, GwoutDispatch.STEP_PUBLISH, e);
            }
            return;
        }

        checkAndUpdateGwoutStatus(gwout.getMsgid());
    }

    /**
     * Gửi bản tin lên AMQP broker và giải phóng tài nguyên khi hoàn tất.
     * Trả về AMQP message-id do broker/SWIM component sinh ra (§4.4.3.3.1).
     */
    private String publish(Gwout gwout, String topic, String recipient,
            String body, String contentType) throws JMSException {
        Session session = null;
        MessageProducer producer = null;
        try {
            session = connectionManager.createSession();
            producer = connectionManager.createProducer(session, topic);

            Message message;
            if ("ftbp".equalsIgnoreCase(gwout.getBodyType())) {
                BytesMessage bytesMsg = session.createBytesMessage();
                byte[] binaryData;
                try {
                    binaryData = java.util.Base64.getDecoder().decode(body);
                } catch (Exception e) {
                    binaryData = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                bytesMsg.writeBytes(binaryData);
                message = bytesMsg;
            } else {
                message = session.createTextMessage(body);
            }

            // EUR Doc 047 v3.0 §4.4.3.3.3: content-type chỉ được text/plain;charset="utf-8" hoặc application/octet-stream
            String ct = contentType != null ? contentType
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "application/octet-stream" : "text/plain; charset=\"utf-8\"");
            message.setStringProperty("JMS_AMQP_CONTENT_TYPE", ct);

            if (gwout.getOrigin() != null) {
                message.setStringProperty("amhs_originator", gwout.getOrigin());
            }
            if (recipient != null) {
                message.setStringProperty("amhs_recipients", recipient);
            }
            // EUR Doc 047 §4.4.3.4.1: amhs_ipm_id chứa IPM-Identifier, không phải MTS-Identifier (amhsid)
            if (gwout.getIpmId() != null) {
                message.setStringProperty("amhs_ipm_id", gwout.getIpmId());
            }
            if (gwout.getAmhsPriority() != null) {
                message.setStringProperty("amhs_ats_pri", gwout.getAmhsPriority());
            }
            if (gwout.getFilingTime() != null) {
                message.setStringProperty("amhs_ats_ft", gwout.getFilingTime());
            }
            if (gwout.getOptionalHeading() != null) {
                message.setStringProperty("amhs_ats_ohi", gwout.getOptionalHeading());
            }
            // EUR Doc 047 §4.4.3.4.8: amhs_subject mang phần tử subject của IPM heading (CTSW001)
            if (gwout.getSubject() != null && !gwout.getSubject().isBlank()) {
                message.setStringProperty("amhs_subject", gwout.getSubject());
            }
            // EUR Doc 047 Table 7 §4.4.3.4.9: dùng body part type đã chuẩn hóa (có thể là
            // general-text-body-part), không tự ý suy giảm về ia5-text-body-part từ bodyType thô
            String bodyPartType = gwout.getBodyPartType() != null ? gwout.getBodyPartType()
                    : ("ftbp".equalsIgnoreCase(gwout.getBodyType()) ? "file-transfer-body-part" : "ia5-text-body-part");
            message.setStringProperty("amhs_bodypart_type", bodyPartType);
            if ("file-transfer-body-part".equals(bodyPartType)) {
                // EUR Doc 047 §4.4.3.4.2 Table 4: các property FTBP là T1 (conditionally translated) —
                // chỉ gán khi biết dữ liệu thật (từ Gwout.ftbp*, nếu nguồn AMHS đã cung cấp), không bịa giá trị
                if (gwout.getFtbpFileName() != null) {
                    message.setStringProperty("amhs_ftbp_file_name", gwout.getFtbpFileName());
                }
                if (gwout.getFtbpObjectSize() != null) {
                    message.setStringProperty("amhs_ftbp_object_size", gwout.getFtbpObjectSize());
                }
                if (gwout.getFtbpLastMod() != null) {
                    message.setStringProperty("amhs_ftbp_last_mod", gwout.getFtbpLastMod());
                }
                // Table 2 §4.4.3.4.10: amhs_registered_identifier là T1 - ánh xạ từ phần tử
                // registered-identifier khi có, vắng mặt thì không gán property.
                if (gwout.getAmhsRegisteredId() != null) {
                    message.setStringProperty("amhs_registered_identifier", gwout.getAmhsRegisteredId());
                }
                // Table 2 §4.4.3.4.11: amhs_user_visible_string là T1 - ánh xạ từ phần tử
                // user-visible-string khi có.
                if (gwout.getAmhsUserVisibleString() != null) {
                    message.setStringProperty("amhs_user_visible_string", gwout.getAmhsUserVisibleString());
                }
                // §4.4.4.6: registered-identifier khác OID mặc định thì user-visible-string bắt buộc
                // phải có kèm. Thiếu thì vẫn gửi bản tin đi nhưng phải báo Control Position.
                if (gwout.getAmhsRegisteredId() != null
                        && !AmqpProperties.isDefaultRegisteredIdentifier(gwout.getAmhsRegisteredId())
                        && gwout.getAmhsUserVisibleString() == null) {
                    log.warn("gwout#{}: amhs_registered_identifier '{}' khác OID mặc định nhưng "
                            + "thiếu amhs_user_visible_string", gwout.getMsgid(), gwout.getAmhsRegisteredId());
                    alertService.create(
                            GwAlert.TYPE_VALIDATION_ERROR,
                            GwAlert.SEV_WARNING,
                            "gwout#" + gwout.getMsgid() + ": amhs_registered_identifier '"
                                    + gwout.getAmhsRegisteredId() + "' khác OID mặc định nhưng thiếu "
                                    + "amhs_user_visible_string (§4.4.4.6)",
                            "gwout", gwout.getMsgid());
                }
            } else if ("ia5-text".equals(bodyPartType) || "ia5-text-body-part".equals(bodyPartType)) {
                message.setStringProperty("amhs_content_encoding", "IA5");
            } else if ("general-text-body-part".equals(bodyPartType)
                    && isTable6ContentEncoding(gwout.getBodyPartCharset())) {
                message.setStringProperty("amhs_content_encoding", gwout.getBodyPartCharset());
            }
            message.setStringProperty("amhs_message_signed", "unsigned");
            message.setStringProperty("amhs_gateway_id", configService.getGatewayId());
            int amqpPriority = gwout.getSwimPriority() != null
                    ? gwout.getSwimPriority()
                    : Message.DEFAULT_PRIORITY;
            producer.send(message, DeliveryMode.PERSISTENT, amqpPriority, Message.DEFAULT_TIME_TO_LIVE);
            return AmqpMessageIdUtil.clean(message.getJMSMessageID());

        } finally {
            if (producer != null) {
                try {
                    producer.close();
                } catch (Exception ignored) {
                }
            }
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Xử lý lỗi phân phối bản tin, tự động lập lịch retry hoặc chuyển thành trạng
     * thái DEAD.
     */
    private void handleFailure(GwoutDispatch dispatch, String step, Exception e) {
        log.error("dispatch#{} FAILED at step={}: {}", dispatch.getId(), step, e.getMessage());
        dispatch.setLastError(step + ": " + e.getMessage());
        dispatch.setFailedStep(step);
        dispatch.setRetryCount(dispatch.getRetryCount() + 1);

        int maxRetry = configService.getInt("RETRY_MAX_COUNT");
        if (dispatch.getRetryCount() >= maxRetry) {
            dispatch.setStatus(GwoutDispatch.STATUS_DEAD);
            alertService.create(
                    GwAlert.TYPE_MESSAGE_DEAD, GwAlert.SEV_CRITICAL,
                    "gwout_dispatch#" + dispatch.getId() + " DEAD at step " + step,
                    "gwout_dispatch", dispatch.getId());
        } else {
            dispatch.setStatus(GwoutDispatch.STATUS_FAILED);
            dispatch.setNextRetryAt(calcNextRetry(dispatch.getRetryCount()));
        }
        gwoutDispatchRepository.save(dispatch);
        checkAndUpdateGwoutStatus(dispatch.getGwoutId());
    }

    /**
     * Kiểm tra trạng thái toàn bộ các bản ghi phân phối để cập nhật trạng thái
     * chung của bản tin gốc (Gwout).
     */
    private void checkAndUpdateGwoutStatus(Long gwoutId) {
        List<GwoutDispatch> all = gwoutDispatchRepository.findByGwoutId(gwoutId);
        if (all.isEmpty())
            return;

        boolean allDone = all.stream()
                .allMatch(d -> GwoutDispatch.STATUS_SENT.equals(d.getStatus())
                        || GwoutDispatch.STATUS_DEAD.equals(d.getStatus()));
        if (!allDone)
            return;

        List<String> deadRecipients = all.stream()
                .filter(d -> GwoutDispatch.STATUS_DEAD.equals(d.getStatus()))
                .map(GwoutDispatch::getRecipient)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        gwoutRepository.findById(gwoutId).ifPresent(gwout -> {
            gwout.setStatus(deadRecipients.isEmpty()
                    ? OutboundStatus.PUBLISHED.getValue()
                    : OutboundStatus.FAILED.getValue());
            gwoutRepository.save(gwout);

            // Hết retry mà vẫn không publish được: ghi traffic log cho từng recipient DEAD để
            // Control Position truy được. Recipient đã SENT vẫn coi là chuyển giao thành công.
            // Việc trả NDR về người gửi X.400 do AMHS Component đảm nhiệm.
            for (String recipient : deadRecipients) {
                conversionService.logAmhsToSwimRejected(gwout,
                        "undeliverable: " + recipient, null,
                        "unable to convert to AMQP due to delivery failure to the SWIM component");
            }
        });
    }

    /**
     * Tính toán thời gian thực hiện retry tiếp theo dựa trên exponential backoff.
     */
    private LocalDateTime calcNextRetry(int retryCount) {
        int[] delays = {
                configService.getInt("RETRY_DELAY_1ST_SECONDS"),
                configService.getInt("RETRY_DELAY_2ND_SECONDS"),
                configService.getInt("RETRY_DELAY_3RD_SECONDS")
        };
        int delay;
        if (retryCount <= delays.length) {
            delay = delays[retryCount - 1];
        } else {
            // Tính delay bằng exponential backoff (max mũ 6).
            int lastDelay = delays[delays.length - 1];
            delay = lastDelay * (int) Math.pow(2, Math.min(retryCount - delays.length, 6));
        }
        return LocalDateTime.now().plusSeconds(delay);
    }

    /**
     * Creates gwout_dispatch for each recipient in gwout.address.
     */
    @Transactional
    public void createDispatches(Gwout gwout) {
        String address = gwout.getAddress();
        if (address == null || address.isBlank()) {
            log.warn("gwout#{} has no recipients, skipping", gwout.getMsgid());
            rejectMessage(gwout, "no-recipients", "unrecognised-OR-name", null,
                    "no_recipients",
                    "gwout#" + gwout.getMsgid() + " has no recipients");
            return;
        }

        // Tách recipient hợp lệ khỏi recipient không chuyển đổi được sang địa chỉ AF.
        // §4.4.8: recipient bị loại phải nhận NDR riêng chứ không được bỏ im lặng — cùng cơ chế
        // per-recipient mà nhánh probe đã áp dụng cho CTSW012. Trước đây chỗ này chỉ log.warn
        // nên bản tin vẫn đi nhưng thiếu người nhận và không để lại dấu vết nào.
        List<String> recipients = new java.util.ArrayList<>();
        List<String> unconvertible = new java.util.ArrayList<>();
        for (String raw : address.split("[,\\s]+")) {
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (MessageValidationService.isValidAftnAddress(candidate)) {
                if (!recipients.contains(candidate)) {
                    recipients.add(candidate);
                }
            } else if (!unconvertible.contains(candidate)) {
                unconvertible.add(candidate);
            }
        }

        if (recipients.isEmpty()) {
            log.warn("gwout#{} has no valid AFTN recipients after filtering", gwout.getMsgid());
            rejectMessage(gwout, "invalid-recipients", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized recipient O/R address",
                    "invalid_recipients: " + String.join(",", unconvertible),
                    "gwout#" + gwout.getMsgid() + " has no valid AFTN recipients");
            return;
        }

        // Tra đích publish theo ĐỊA CHỈ của từng recipient. EUR Doc 047 Appendix A §2.2 xác định
        // các AMQP consumer là "configuration parameters which are jointly set up", còn CTSW009
        // kiểm tra việc phân phối dựa trên địa chỉ recipient - không dựa trên nội dung bản tin.
        // Mỗi recipient có thể ra một topic khác nhau; processDispatch() gom lại theo
        // (gwout, topic) nên một IPM vẫn chỉ sinh một message AMQP cho mỗi topic (§4.4.3.4.4).
        java.util.LinkedHashMap<String, String> topicByRecipient = new java.util.LinkedHashMap<>();
        List<String> unroutable = new java.util.ArrayList<>();
        for (String recipient : recipients) {
            var ruleOpt = routingService.findTopicForRecipient(recipient);
            if (ruleOpt.isEmpty()) {
                log.warn("gwout#{}: recipient '{}' không khớp rule định tuyến OUT nào",
                        gwout.getMsgid(), recipient);
                unroutable.add(recipient);
                continue;
            }
            topicByRecipient.put(recipient, ruleOpt.get().getSendTopic());
        }

        if (topicByRecipient.isEmpty()) {
            log.warn("gwout#{} không có recipient nào tra được đích publish", gwout.getMsgid());
            rejectMessage(gwout, "no-routing-rule", "unrecognised-OR-name",
                    "unable to convert to AMQP due to unrecognized recipient O/R address",
                    "routing_failed: no rule for recipients " + String.join(",", recipients),
                    "gwout#" + gwout.getMsgid() + " rejected: không có rule định tuyến cho recipient ["
                            + String.join(",", recipients) + "]");
            return;
        }

        // NDR cho từng recipient không chuyển giao được - địa chỉ sai khuôn AFTN, hoặc đúng khuôn
        // nhưng không có rule định tuyến nào khai báo. Bản tin vẫn đi tới các recipient còn lại
        // (§4.4.6.5/§4.4.6.6 - report của X.400 mang per-recipient-fields nên DR và NDR cùng tồn
        // tại trong một report).
        List<String> undeliverable = new java.util.ArrayList<>(unconvertible);
        undeliverable.addAll(unroutable);
        if (!undeliverable.isEmpty()) {
            log.warn("gwout#{} có {} recipient không chuyển giao được ({}), sinh NDR riêng cho từng recipient",
                    gwout.getMsgid(), undeliverable.size(), String.join(",", undeliverable));
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "gwout#" + gwout.getMsgid() + ": NDR cho recipient ["
                            + String.join(",", undeliverable) + "], bản tin vẫn chuyển tới ["
                            + String.join(",", topicByRecipient.keySet()) + "]",
                    "gwout", gwout.getMsgid());
            for (String recipient : unconvertible) {
                recordRecipientNdr(gwout, recipient, "ndr_unrecognised_recipient");
            }
            for (String recipient : unroutable) {
                recordRecipientNdr(gwout, recipient, "ndr_no_route");
            }
        }

        for (var entry : topicByRecipient.entrySet()) {
            GwoutDispatch dispatch = new GwoutDispatch();
            dispatch.setGwoutId(gwout.getMsgid());
            dispatch.setRecipient(entry.getKey());
            dispatch.setTopic(entry.getValue());
            dispatch.setStatus(GwoutDispatch.STATUS_PENDING);
            gwoutDispatchRepository.save(dispatch);
        }

        gwoutRepository.save(gwout);
        log.debug("gwout#{} -> {} dispatch(es) created, topics={}", gwout.getMsgid(),
                topicByRecipient.size(), new java.util.LinkedHashSet<>(topicByRecipient.values()));
    }

    /**
     * Kích thước thật của payload tính bằng byte, dùng cho phép so sánh với "Maximum message
     * data size" (EUR Doc 047 §4.4.2.6 / CTSW006).
     * <p>
     * Với file-transfer-body-part, nội dung nhị phân được lưu base64 trong {@code gwout.text}
     * (xem {@link #publish}); đo trực tiếp trên chuỗi sẽ phồng khoảng 33% và có thể từ chối nhầm
     * bản tin nằm sát ngưỡng. Trả về null khi không có payload để validator tự xử lý.
     */
    /**
     * EUR Doc 047 Table 6 / §4.4.3.4.9: các giá trị hợp lệ của amhs_content_encoding.
     */
    private boolean isTable6ContentEncoding(String charset) {
        return "IA5".equals(charset) || "ISO-646".equals(charset) || "ISO-8859-1".equals(charset);
    }

    private Integer payloadByteSize(Gwout gwout) {
        String body = gwout.getText();
        if (body == null) {
            return null;
        }
        if ("ftbp".equalsIgnoreCase(gwout.getBodyType())) {
            try {
                return java.util.Base64.getDecoder().decode(body).length;
            } catch (IllegalArgumentException e) {
                // Không phải base64 hợp lệ -> nội dung đã là dữ liệu thô, đo trực tiếp
                log.debug("gwout#{} body_type=ftbp nhưng text không phải base64, đo kích thước trực tiếp",
                        gwout.getMsgid());
            }
        }
        return body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Suy ra non-delivery-diagnostic-code theo ma trận EUR Doc 047 §4.4.2.6/§4.4.2.7
     * từ thông báo lỗi của MessageValidationService (đã gắn sẵn mã chẩn đoán trong ngoặc).
     */
    private String ndrDiagnosticFor(String errorMessage) {
        if (errorMessage == null) return null;
        if (errorMessage.contains("content-too-long")) return "content-too-long";
        if (errorMessage.contains("too-many-recipients")) return "too-many-recipients";
        return null;
    }

    /**
     * Suy ra supplementary-information của NDR theo Appendix A:
     * CTSW006 (vượt "Maximum message data size") và CTSW010 (vượt "Maximum message
     * number of recipients") đều bắt buộc NDR mang đúng chuỗi mô tả tương ứng.
     */
    private String ndrSupplementaryFor(String errorMessage) {
        if (errorMessage == null) return null;
        if (errorMessage.contains("content-too-long")) return "unable to convert to AMQP due to the content size";
        if (errorMessage.contains("too-many-recipients")) return "unable to convert to AMQP due to number of recipients";
        return null;
    }

    /**
     * CTSW011 - CTSW013: Xử lý bản tin Probe nhận từ AMHS.
     */
    private void processAmhsProbe(Gwout gwout) {
        // 1. CTSW013 (§4.4.6.4): originator không chuyển đổi được sang AF-address -> từ chối
        //    probe cho TOÀN BỘ recipient.
        String originator = gwout.getOrigin();
        if (!authorizationService.isAmhsUserAuthorized(originator)) {
            rejectProbe(gwout, "Unknown originator: " + originator, "unknown-originator",
                    "invalid-arguments",
                    "unable to convert to AMQP due to unrecognized originator O/R address");
            return;
        }

        String recipients = gwout.getAddress();
        if (recipients == null || recipients.isBlank()) {
            rejectProbe(gwout, "Recipients list is empty", "empty-recipients", "unrecognised-OR-name", null);
            return;
        }
        String[] recipientArray = recipients.trim().split("[,\\s]+");

        // 2. CTSW016 (§4.4.6.1): current encoded-information-types của probe cũng phải hợp lệ.
        MessageValidationService.ValidationResult eitResult =
                validationService.validateEncodedInformationTypes(gwout.getOriginEit());
        if (!eitResult.isValid()) {
            rejectProbe(gwout, "Probe EIT rejected: " + eitResult.getErrorMessage(), "unsupported-eit",
                    "encoded-information-types-unsupported", null);
            return;
        }

        // 3. CTSW011 Probe 3 (§4.4.6.2): content-length khai báo trong probe vượt
        //    "Maximum message data size" -> NDR content-too-long.
        Integer contentLength = gwout.getContentLength();
        if (contentLength != null) {
            int maxSize = configService.getMaxMsgDataSize();
            if (maxSize > 0 && contentLength > maxSize) {
                rejectProbe(gwout,
                        String.format("Probe content-length %d exceeds maximum %d", contentLength, maxSize),
                        "content-too-long", "content-too-long",
                        "unable to convert to AMQP due to the content size");
                return;
            }
        }

        // 4. CTSW011 Probe 4/5 (§4.4.6.3): số recipient vượt "Maximum message number of recipients".
        int maxRecipients = configService.getMaxMsgRecipients();
        if (maxRecipients > 0 && recipientArray.length > maxRecipients) {
            rejectProbe(gwout,
                    String.format("Probe addresses %d recipients, exceeds maximum %d",
                            recipientArray.length, maxRecipients),
                    "too-many-recipients", "too-many-recipients",
                    "unable to convert to AMQP due to number of recipients");
            return;
        }

        // 5. CTSW012: xét TỪNG recipient để ghi traffic log riêng cho recipient chuyển đổi được
        //    và recipient không chuyển đổi được sang AF-address. Combined report (DR cho
        //    recipient hợp lệ + NDR cho recipient còn lại) do AMHS Component dựng và phát.
        List<String> deliverable = new java.util.ArrayList<>();
        List<String> undeliverable = new java.util.ArrayList<>();
        for (String recipient : recipientArray) {
            var formatResult = validationService.validateAftnAddress(recipient, "Recipient");
            if (!formatResult.isValid() || !isRecipientKnown(recipient)) {
                undeliverable.add(recipient);
            } else {
                deliverable.add(recipient);
            }
        }

        // Ghi traffic log theo từng recipient để Control Position tra được kết quả probe.
        for (String recipient : deliverable) {
            conversionService.logAmhsToSwim(gwout, null, "OK", "probe_deliverable: " + recipient);
        }
        for (String recipient : undeliverable) {
            conversionService.logAmhsToSwimRejected(gwout,
                    "probe_unknown_recipient: " + recipient, "unrecognised-OR-name", null);
        }

        if (!undeliverable.isEmpty()) {
            log.warn("Probe gwout#{}: {} recipient(s) không chuyển đổi được sang AF-address ({}), "
                            + "{} recipient(s) chuyển đổi được",
                    gwout.getMsgid(), undeliverable.size(), String.join(",", undeliverable), deliverable.size());
            alertService.create(
                    GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                    "Probe gwout#" + gwout.getMsgid() + ": recipient không xác định ["
                            + String.join(",", undeliverable) + "]"
                            + (deliverable.isEmpty() ? "" : ", hợp lệ [" + String.join(",", deliverable) + "]"),
                    "gwout", gwout.getMsgid());
            gwout.setRejectionReason("unknown-recipient");
            gwout.setRejectionSource("AMHS");
            gwout.setRejectionDiagnostic("unrecognised-OR-name");
        }

        // Probe được coi là xử lý xong khi có ít nhất một recipient nhận DR; nếu mọi recipient
        // đều bị từ chối thì probe thất bại hoàn toàn.
        gwout.setStatus(deliverable.isEmpty()
                ? OutboundStatus.FAILED.getValue()
                : OutboundStatus.PUBLISHED.getValue());
        gwoutRepository.save(gwout);

        if (!deliverable.isEmpty()) {
            log.info("Probe gwout#{} conveyance test OK cho {} recipient", gwout.getMsgid(), deliverable.size());
        }
    }

    /**
     * Từ chối probe cho toàn bộ recipient: log traffic và báo Control Position
     * (§3.1.1.1 - probe không được chuyển sang SWIM nhưng phải được log và báo CP).
     * Việc phát NDR ra đường truyền X.400 do AMHS Component đảm nhiệm.
     */
    private void rejectProbe(Gwout gwout, String reason, String rejectionCode, String ndrDiagnostic,
            String supplementaryInfo) {
        log.warn("Probe gwout#{} REJECTED: {}", gwout.getMsgid(), reason);
        alertService.create(
                GwAlert.TYPE_VALIDATION_ERROR, GwAlert.SEV_WARNING,
                "Probe gwout#" + gwout.getMsgid() + " rejected: " + reason,
                "gwout", gwout.getMsgid());
        gwout.setStatus(OutboundStatus.FAILED.getValue());
        gwout.setRejectionReason(rejectionCode);
        gwout.setRejectionSource("AMHS");
        gwout.setRejectionDiagnostic(ndrDiagnostic);
        gwoutRepository.save(gwout);
        conversionService.logAmhsToSwimRejected(gwout, "probe_rejected_" + rejectionCode + ": " + reason,
                ndrDiagnostic, supplementaryInfo);
    }

    /**
     * CTSW012 (§4.4.6.5): recipient của probe có chuyển đổi được sang địa chỉ AF hay không.
     * <p>
     * Phép chuyển O/R address → AF-address là xác định được từ chính khuôn địa chỉ, nên tiêu chí
     * đúng là khuôn AFTN 8 chữ cái. Trước đây hàm này hỏi "recipient có nằm trong whitelist hoặc
     * trong cột {@code recipients} của rule IN không" — sai bản chất, vì rule IN mô tả việc gateway
     * có route SWIM cho địa chỉ đó, không liên quan tới khả năng chuyển đổi địa chỉ. Với cấu hình
     * thật (whitelist rỗng, rule IN chỉ có hai địa chỉ), mọi recipient khác đều bị NDR
     * "unrecognised-OR-name" và CTSW011/CTSW012 không thể pass.
     * <p>
     * {@code AUTHORIZED_AMHS_ADDRESSES} được giữ làm whitelist SIẾT tuỳ chọn: khi có khai báo thì
     * chỉ địa chỉ trong danh sách mới được chấp nhận; khi rỗng thì chỉ xét khuôn.
     */
    private boolean isRecipientKnown(String recipient) {
        if (!MessageValidationService.isValidAftnAddress(recipient)) {
            return false;
        }

        String whitelist;
        try {
            whitelist = configService.get("AUTHORIZED_AMHS_ADDRESSES");
        } catch (Exception e) {
            // Cấu hình chưa khai báo -> không siết
            return true;
        }
        if (whitelist == null || whitelist.isBlank()) {
            return true;
        }
        return containsExact(whitelist, recipient);
    }

    private boolean containsExact(String configValue, String target) {
        if (configValue == null || configValue.isBlank() || target == null || target.isBlank()) {
            return false;
        }
        String[] items = configValue.split("[,;\\s]+");
        for (String item : items) {
            if (item.trim().equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }
}