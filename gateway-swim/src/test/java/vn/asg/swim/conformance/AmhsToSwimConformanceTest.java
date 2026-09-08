package vn.asg.swim.conformance;

import jakarta.jms.BytesMessage;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.entity.GwoutReport;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.entity.Routing;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutReportRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.scheduler.AmhsToGwoutSyncScheduler;
import vn.asg.swim.service.AlertService;
import vn.asg.swim.service.AuthorizationService;
import vn.asg.swim.service.ConfigService;
import vn.asg.swim.service.ConnectionManagerService;
import vn.asg.swim.service.MessageConversionService;
import vn.asg.swim.service.MessageValidationService;
import vn.asg.swim.service.OutboundDispatchService;
import vn.asg.swim.service.ReportService;
import vn.asg.swim.service.RoutingService;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Đối chiếu đầu-cuối chiều AMHS → SWIM với EUR Doc 047 Appendix A (CTSW001–CTSW020).
 * <p>
 * Khác với các test đơn vị hiện có, lớp này chạy TRỌN đường đi thật của một bản tin:
 * <pre>
 *   mtcu_tmp/mtcu_to  →  AmhsToGwoutSyncScheduler  →  gwout
 *                     →  OutboundDispatchService.processOutboundMessage  (kiểm tra + transform)
 *                     →  createDispatches                                (định tuyến từng recipient)
 *                     →  processDispatch                                 (publish AMQP)
 *                     →  AMQP application properties nhận được ở đầu SWIM
 * </pre>
 * {@link MessageValidationService} và {@link ReportService} dùng bản THẬT (không mock) để các
 * phép kiểm tra cú pháp, ngưỡng cấu hình và việc sinh DR/NDR được thực thi đúng như production;
 * chỉ broker JMS, kho dữ liệu và các dịch vụ ngoại vi mới bị mock.
 * <p>
 * Mỗi test khẳng định đúng những gì Appendix A yêu cầu quan sát được ở "AMQP test interface"
 * và ở "AMHS interface" (bộ ba phần tử của NDR: non-delivery-reason-code,
 * non-delivery-diagnostic-code, supplementary-information).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmhsToSwimConformanceTest {

    // ---------- Mock hạ tầng ----------
    @Mock private EntityManager entityManager;
    @Mock private Query query;
    @Mock private GwoutRepository gwoutRepository;
    @Mock private GwoutDispatchRepository gwoutDispatchRepository;
    @Mock private GwoutReportRepository gwoutReportRepository;
    @Mock private ConfigService configService;
    @Mock private RoutingService routingService;
    @Mock private MessageConversionService conversionService;
    @Mock private AuthorizationService authorizationService;
    @Mock private AlertService alertService;
    @Mock private ConnectionManagerService connectionManager;

    // ---------- Đối tượng thật ----------
    private MessageValidationService validationService;
    private ReportService reportService;
    private AmhsToGwoutSyncScheduler syncScheduler;
    private OutboundDispatchService dispatchService;

    // ---------- Trạng thái ghi lại trong một lần chạy ----------
    private final List<Gwout> gwouts = new ArrayList<>();
    private final List<GwoutDispatch> dispatches = new ArrayList<>();
    private final List<GwoutReport> reports = new ArrayList<>();
    private final List<Publish> publishes = new ArrayList<>();
    private final List<String> alerts = new ArrayList<>();

    private final Map<Message, Map<String, String>> messageProps = new IdentityHashMap<>();
    private final Map<Message, String> messageText = new IdentityHashMap<>();
    private final Map<Message, byte[]> messageBytes = new IdentityHashMap<>();

    private long gwoutSeq = 0;
    private long dispatchSeq = 0;
    private long messageSeq = 0;
    private String currentTopic;
    private byte[] ftbpData;

    /** Một lần publish quan sát được ở "AMQP test interface". */
    static class Publish {
        String topic;
        Map<String, String> props;
        String amqpValue;   // amqp-value (TextMessage)
        byte[] data;        // data (BytesMessage)
        int priority;
        int deliveryMode;

        String prop(String name) {
            return props.get(name);
        }
    }

    // =====================================================================================
    // Harness
    // =====================================================================================

    @BeforeEach
    void setUp() throws Exception {
        validationService = new MessageValidationService(configService);
        reportService = new ReportService(gwoutReportRepository);
        syncScheduler = new AmhsToGwoutSyncScheduler(entityManager, gwoutRepository, alertService);
        dispatchService = new OutboundDispatchService(connectionManager, routingService, conversionService,
                validationService, authorizationService, configService, alertService, reportService,
                gwoutDispatchRepository, gwoutRepository);

        // ----- Cấu hình mặc định -----
        when(configService.getConversionDir()).thenReturn("BOTH");
        when(configService.getMaxMsgDataSize()).thenReturn(0);       // 0 = không giới hạn
        when(configService.getMaxMsgRecipients()).thenReturn(512);   // §3.3.2.4
        when(configService.isNonIso646RepertoireAllowed()).thenReturn(false);
        when(configService.getGatewayId()).thenReturn("ASG-GW-01");
        when(configService.get("AUTHORIZED_AMHS_ADDRESSES")).thenReturn("");
        when(configService.getInt("RETRY_MAX_COUNT")).thenReturn(3);
        when(configService.getInt("RETRY_DELAY_1ST_SECONDS")).thenReturn(30);
        when(configService.getInt("RETRY_DELAY_2ND_SECONDS")).thenReturn(120);
        when(configService.getInt("RETRY_DELAY_3RD_SECONDS")).thenReturn(300);

        when(authorizationService.isAmhsUserAuthorized(anyString())).thenReturn(true);

        Routing rule = new Routing();
        rule.setRecipients("*");
        rule.setSendTopic("ats.met.metar");
        when(routingService.findTopicForRecipient(anyString())).thenReturn(Optional.of(rule));

        // ----- Kho dữ liệu -----
        when(gwoutRepository.saveAndFlush(any(Gwout.class))).thenAnswer(inv -> {
            Gwout g = inv.getArgument(0);
            if (g.getMsgid() == null) {
                g.setMsgid(++gwoutSeq);
                gwouts.add(g);
            }
            return g;
        });
        when(gwoutRepository.save(any(Gwout.class))).thenAnswer(inv -> {
            Gwout g = inv.getArgument(0);
            if (g.getMsgid() == null) {
                g.setMsgid(++gwoutSeq);
                gwouts.add(g);
            }
            return g;
        });
        when(gwoutRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return gwouts.stream().filter(g -> id.equals(g.getMsgid())).findFirst();
        });

        when(gwoutDispatchRepository.save(any(GwoutDispatch.class))).thenAnswer(inv -> {
            GwoutDispatch d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(++dispatchSeq);
                dispatches.add(d);
            }
            return d;
        });
        when(gwoutDispatchRepository.findByGwoutId(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return dispatches.stream().filter(d -> id.equals(d.getGwoutId())).toList();
        });

        when(gwoutReportRepository.save(any(GwoutReport.class))).thenAnswer(inv -> {
            GwoutReport r = inv.getArgument(0);
            reports.add(r);
            return r;
        });
        when(gwoutReportRepository.findByGwoutIdAndRecipientAndReportType(anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> reports.stream()
                        .filter(r -> inv.getArgument(0).equals(r.getGwoutId())
                                && inv.getArgument(1).equals(r.getRecipient())
                                && inv.getArgument(2).equals(r.getReportType()))
                        .findFirst());

        doAnswer(inv -> {
            alerts.add(inv.getArgument(2, String.class));
            return null;
        }).when(alertService).create(anyString(), anyString(), anyString(), anyString(), any());

        // ----- Broker JMS giả lập, ghi lại mọi thứ đi ra "AMQP test interface" -----
        Session session = mock(Session.class);
        MessageProducer producer = mock(MessageProducer.class);
        when(connectionManager.createSession()).thenReturn(session);
        when(connectionManager.createProducer(any(), anyString())).thenAnswer(inv -> {
            currentTopic = inv.getArgument(1);
            return producer;
        });

        when(session.createTextMessage(anyString())).thenAnswer(inv -> {
            TextMessage m = mock(TextMessage.class);
            registerMessage(m);
            messageText.put(m, inv.getArgument(0));
            return m;
        });
        when(session.createBytesMessage()).thenAnswer(inv -> {
            BytesMessage m = mock(BytesMessage.class);
            registerMessage(m);
            doAnswer(w -> {
                messageBytes.put(m, w.getArgument(0));
                return null;
            }).when(m).writeBytes(any(byte[].class));
            return m;
        });

        doAnswer(inv -> {
            Message m = inv.getArgument(0);
            Publish p = new Publish();
            p.topic = currentTopic;
            p.props = messageProps.getOrDefault(m, Map.of());
            p.amqpValue = messageText.get(m);
            p.data = messageBytes.get(m);
            p.deliveryMode = inv.getArgument(1);
            p.priority = inv.getArgument(2);
            publishes.add(p);
            return null;
        }).when(producer).send(any(Message.class), anyInt(), anyInt(), anyLong());
    }

    private void registerMessage(Message m) throws Exception {
        Map<String, String> props = new LinkedHashMap<>();
        messageProps.put(m, props);
        doAnswer(inv -> {
            props.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(m).setStringProperty(anyString(), anyString());
        when(m.getJMSMessageID()).thenReturn("ID:conformance-" + (++messageSeq));
    }

    /**
     * Chạy trọn pipeline cho các dòng mtcu_tmp/mtcu_to đã dựng và trả về dòng gwout sinh ra.
     * Dừng lại đúng ở bước mà bản tin bị từ chối, giống hệt production.
     */
    private Gwout deliver(List<Object[]> rows) {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(ftbpData);
        when(query.getResultList()).thenReturn(rows);

        int before = gwouts.size();
        syncScheduler.syncAmhsToGwout();
        assertTrue(gwouts.size() > before, "Scheduler đồng bộ phải sinh ra dòng gwout");
        Gwout gwout = gwouts.get(gwouts.size() - 1);

        if (!Integer.valueOf(OutboundStatus.PENDING.getValue()).equals(gwout.getStatus())) {
            return gwout; // bị loại ngay ở bước đồng bộ
        }

        dispatchService.processOutboundMessage(gwout);
        if (!Integer.valueOf(OutboundStatus.TRANSFORMED.getValue()).equals(gwout.getStatus())) {
            return gwout; // bị từ chối ở bước kiểm tra/transform
        }

        dispatchService.createDispatches(gwout);
        List<GwoutDispatch> created = dispatches.stream()
                .filter(d -> gwout.getMsgid().equals(d.getGwoutId()))
                .toList();
        if (created.isEmpty()) {
            return gwout; // không recipient nào định tuyến được
        }
        // Poller gọi processDispatch cho từng dòng; nhánh gộp bên trong tự bỏ qua dòng đã publish
        for (GwoutDispatch d : created) {
            dispatchService.processDispatch(d);
        }
        return gwout;
    }

    private Gwout deliver(Object[]... rows) {
        return deliver(List.of(rows));
    }

    // ---------- dựng dòng mtcu_tmp JOIN mtcu_to (22 cột, đúng thứ tự SELECT) ----------

    private Object[] row(long id, String priority, String filingTime, String text) {
        Object[] r = new Object[22];
        r[0] = id;                       // mtcu_tmp.id
        r[1] = text;                     // content
        r[2] = filingTime;               // atsFilingTime
        r[3] = priority;                 // atsPriority
        r[4] = null;                     // atsOhi
        r[5] = "401";                    // bodyPartType (ia5-text)
        r[6] = "IPM-" + id;              // ipmId
        r[7] = "MSG-" + id;              // messageId (MTS-Identifier)
        r[8] = orName("VVNBZTZX");       // orAddress (originator)
        r[9] = orName("VVHHZTZX");       // recipient_address
        r[10] = null;                    // bodyPartCharacterSet
        r[11] = null;                    // file_name
        r[12] = 0L;                      // OCTET_LENGTH(data)
        r[13] = null;                    // unused
        r[14] = 1;                       // numberOfAttachment
        r[15] = null;                    // originEncodeInformationType
        r[16] = null;                    // reportRequest
        r[17] = null;                    // mtaReportRequest
        r[18] = 22;                      // contentType = interpersonal-messaging-1988
        r[19] = null;                    // subject
        r[20] = null;                    // precedence
        r[21] = null;                    // responsibility
        return r;
    }

    private Object[] recipientRow(Object[] base, String recipient) {
        Object[] r = base.clone();
        r[9] = orName(recipient);
        return r;
    }

    private static String orName(String cn) {
        return "/CN=" + cn + "/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
    }

    // ---------- tiện ích khẳng định ----------

    private Publish onlyPublish() {
        assertEquals(1, publishes.size(),
                "§4.4.3.4.4: một IPM chỉ được sinh đúng một bản tin AMQP cho mỗi topic");
        return publishes.get(0);
    }

    private void assertPublished(Gwout g) {
        assertEquals(OutboundStatus.PUBLISHED.getValue(), g.getStatus(),
                "bản tin phải được chuyển đổi và publish sang SWIM");
    }

    private void assertRejected(Gwout g, String diagnostic, String supplementary) {
        assertEquals(OutboundStatus.FAILED.getValue(), g.getStatus(), "bản tin phải bị từ chối");
        assertTrue(publishes.isEmpty(), "bản tin bị từ chối không được publish sang AMQP");
        List<GwoutReport> ndrs = ndrsFor(g);
        assertFalse(ndrs.isEmpty(), "phải sinh NDR trả về AMHS");
        for (GwoutReport ndr : ndrs) {
            assertEquals(GwoutReport.REASON_UNABLE_TO_TRANSFER, ndr.getReasonCode(),
                    "non-delivery-reason-code phải là unable-to-transfer");
            assertEquals(diagnostic, ndr.getDiagnosticCode(), "non-delivery-diagnostic-code");
            if (supplementary != null) {
                assertEquals(supplementary, ndr.getSupplementaryInfo(), "supplementary-information");
            }
        }
    }

    private List<GwoutReport> ndrsFor(Gwout g) {
        return reports.stream()
                .filter(r -> g.getMsgid().equals(r.getGwoutId())
                        && GwoutReport.TYPE_NDR.equals(r.getReportType()))
                .toList();
    }

    private List<GwoutReport> drsFor(Gwout g) {
        return reports.stream()
                .filter(r -> g.getMsgid().equals(r.getGwoutId())
                        && GwoutReport.TYPE_DR.equals(r.getReportType()))
                .toList();
    }

    private void reset() {
        gwouts.clear();
        dispatches.clear();
        reports.clear();
        publishes.clear();
        alerts.clear();
        ftbpData = null;
    }

    // =====================================================================================
    // CTSW001 — Convert an incoming IPM to AMQP format
    // =====================================================================================

    @Nested
    @DisplayName("CTSW001 Convert an incoming IPM to AMQP format")
    class Ctsw001 {

        @Test
        @DisplayName("Basic IPM: 5 mức ATS-message-priority → amhs_ats_pri + AMQP header priority (Table 3/5)")
        void basicIpmPriorities() {
            String[][] cases = {
                    { "KK", "2" }, { "GG", "3" }, { "FF", "4" }, { "DD", "5" }, { "SS", "6" }
            };
            for (int i = 0; i < cases.length; i++) {
                reset();
                String pri = cases[i][0];
                String ft = String.format("07%02d30", 10 + i);
                String text = "METAR VVNB " + ft + "Z 15004KT 9999 NOSIG=";

                Gwout g = deliver(row(100 + i, pri, ft, text));

                assertPublished(g);
                Publish p = onlyPublish();
                assertEquals(pri, p.prop("amhs_ats_pri"), "§4.4.3.4.3 amhs_ats_pri");
                assertEquals(Integer.parseInt(cases[i][1]), p.priority,
                        "§4.4.3.2.2 Table 3: AMQP header priority của " + pri);
                assertEquals(ft, p.prop("amhs_ats_ft"), "§4.4.3.4.5 amhs_ats_ft = filing time");
                assertEquals("IPM-" + (100 + i), p.prop("amhs_ipm_id"),
                        "§4.4.3.4.1 amhs_ipm_id mang IPM-Identifier, không phải MTS-Identifier");
                assertEquals(text, p.amqpValue, "amqp-value phải trùng ATS-message-text gốc");
                assertNull(p.data, "phần tử AMQP 'data' phải rỗng với bản tin text");
                assertEquals(DeliveryMode.PERSISTENT, p.deliveryMode, "§4.4.3.2.1 durable = true");
                assertEquals("VVNBZTZX", p.prop("amhs_originator"), "§4.4.3.4.7 amhs_originator");
                assertEquals("VVHHZTZX", p.prop("amhs_recipients"), "§4.4.3.4.4 amhs_recipients");
                assertEquals(8, p.prop("amhs_originator").length(), "địa chỉ phải là 8 chữ cái");
            }
        }

        @Test
        @DisplayName("Basic IPM: các application property cố định của §4.4.3.3 / §4.4.3.4.10")
        void fixedApplicationProperties() {
            Gwout g = deliver(row(115, "FF", "070430", "METAR VVNB="));

            assertPublished(g);
            Publish p = onlyPublish();
            assertEquals("text/plain; charset=\"utf-8\"", p.prop("JMS_AMQP_CONTENT_TYPE"),
                    "§4.4.3.3.3: content-type chỉ được text/plain;charset=\"utf-8\" "
                            + "hoặc application/octet-stream");
            assertEquals("unsigned", p.prop("amhs_message_signed"), "§4.4.3.4.10 amhs_message_signed");
            assertEquals("ASG-GW-01", p.prop("amhs_gateway_id"), "định danh gateway");
            assertNotNull(g.getAmqpMessageId(), "§4.4.3.3.1: phải lưu lại AMQP message-id");
            assertEquals("ats.met.metar", p.topic, "publish đúng topic của rule định tuyến");
        }

        @Test
        @DisplayName("FTBP: content-type application/octet-stream và các property Table 4")
        void ftbpApplicationProperties() {
            ftbpData = "BINARY-PAYLOAD".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Object[] r = row(116, "FF", "070430", "IGNORED");
            r[5] = "403";
            r[11] = "flightplan.pdf";
            r[12] = (long) ftbpData.length;

            Gwout g = deliver(r);

            assertPublished(g);
            Publish p = onlyPublish();
            assertEquals("application/octet-stream", p.prop("JMS_AMQP_CONTENT_TYPE"), "§4.4.3.3.3");
            assertEquals("file-transfer-body-part", p.prop("amhs_bodypart_type"), "Table 6");
            assertEquals("flightplan.pdf", p.prop("amhs_ftbp_file_name"), "§4.4.3.4.2 Table 4");
            assertEquals(String.valueOf(ftbpData.length), p.prop("amhs_ftbp_object_size"),
                    "§4.4.3.4.2 Table 4 - object-size là kích thước dữ liệu gốc");
            assertNull(p.prop("amhs_content_encoding"),
                    "FTBP không có repertoire nên không gán amhs_content_encoding");
        }

        @Test
        @DisplayName("Basic IPM: optional-heading-information rỗng → không gán amhs_ats_ohi")
        void basicIpmWithoutOhi() {
            Gwout g = deliver(row(110, "FF", "070430", "METAR VVNB="));
            assertPublished(g);
            assertNull(onlyPublish().prop("amhs_ats_ohi"),
                    "OHI rỗng thì không được bịa ra application property");
        }

        @Test
        @DisplayName("Extended IPM: precedence 14/28/57/71/107 → amhs_ats_pri theo Table 5")
        void extendedIpmPrecedence() {
            int[] precedences = { 14, 28, 57, 71, 107 };
            String[] expectedPri = { "KK", "GG", "FF", "DD", "SS" };
            int[] expectedAmqp = { 2, 3, 4, 5, 6 };

            for (int i = 0; i < precedences.length; i++) {
                reset();
                Object[] r = row(120 + i, "KK", "07043" + i, "EXTENDED IPM " + i);
                r[19] = "SUBJECT-" + i;      // IPM heading subject
                r[20] = precedences[i];      // recipient-extensions precedence
                r[21] = Boolean.TRUE;        // responsibility = responsible

                Gwout g = deliver(r);

                assertPublished(g);
                Publish p = onlyPublish();
                assertEquals(expectedPri[i], p.prop("amhs_ats_pri"),
                        "Table 5: precedence " + precedences[i] + " ↔ ATS-message-priority");
                assertEquals(expectedAmqp[i], p.priority,
                        "Table 3: AMQP priority của precedence " + precedences[i]);
                assertEquals("SUBJECT-" + i, p.prop("amhs_subject"), "§4.4.3.4.8 amhs_subject");
                assertEquals("07043" + i, p.prop("amhs_ats_ft"),
                        "§4.4.3.4.5 amhs_ats_ft mang authorization-time của Extended IPM");
            }
        }

        @Test
        @DisplayName("Extended IPM 2 recipient (precedence 107 và 14) → lấy precedence CAO NHẤT")
        void extendedIpmHighestPrecedenceWins() {
            Object[] base = row(130, "KK", "070435", "EXTENDED IPM HIGHEST");
            base[20] = 14;
            base[21] = Boolean.TRUE;
            Object[] second = recipientRow(base, "VVCIZTZX");
            second[20] = 107;

            Gwout g = deliver(base, second);

            assertPublished(g);
            Publish p = onlyPublish();
            assertEquals("SS", p.prop("amhs_ats_pri"),
                    "amhs_ats_pri lấy từ precedence cao nhất trong các recipient (107)");
            assertEquals(6, p.priority, "AMQP priority tương ứng SS = 6");
            assertEquals("VVHHZTZX,VVCIZTZX", p.prop("amhs_recipients"),
                    "§4.4.3.4.4: nhiều recipient phân cách bằng dấu phẩy");
        }
    }

    // =====================================================================================
    // CTSW002 — optional-heading-information / originators-reference
    // =====================================================================================

    @Test
    @DisplayName("CTSW002: OHI trong ATS-message-header → amhs_ats_ohi (§4.4.3.4.6)")
    void ctsw002_ohiMappedToApplicationProperty() {
        Object[] r = row(200, "FF", "070430", "METAR VVNB=");
        r[4] = "OHI-FF-TEXT";

        Gwout g = deliver(r);

        assertPublished(g);
        assertEquals("OHI-FF-TEXT", onlyPublish().prop("amhs_ats_ohi"));
    }

    @Test
    @DisplayName("CTSW002: bản tin SS có OHI vẫn chuyển đổi bình thường")
    void ctsw002_ohiOnSsMessage() {
        Object[] r = row(201, "SS", "070431", "URGENT=");
        r[4] = "OHI-SS-TEXT";

        Gwout g = deliver(r);

        assertPublished(g);
        Publish p = onlyPublish();
        assertEquals("OHI-SS-TEXT", p.prop("amhs_ats_ohi"));
        assertEquals("SS", p.prop("amhs_ats_pri"));
    }

    @Test
    @DisplayName("CTSW002: originators-reference của Extended IPM (precedence 57/107) → amhs_ats_ohi")
    void ctsw002_originatorsReferenceOnExtendedIpm() {
        for (int precedence : new int[] { 57, 107 }) {
            reset();
            Object[] r = row(202 + precedence, "KK", "070432", "EXTENDED=");
            r[4] = "ORIG-REF-" + precedence;
            r[20] = precedence;
            r[21] = Boolean.TRUE;

            Gwout g = deliver(r);

            assertPublished(g);
            assertEquals("ORIG-REF-" + precedence, onlyPublish().prop("amhs_ats_ohi"),
                    "originators-reference dùng chung AMQP property amhs_ats_ohi");
        }
    }

    // =====================================================================================
    // CTSW003 — Generate a DR for a successfully translated IPM
    // =====================================================================================

    @Test
    @DisplayName("CTSW003: 6 tổ hợp per-recipient-indicators quyết định có sinh DR hay không")
    void ctsw003_deliveryReportMatrix() {
        // {originator-report-request, originating-MTA-report-request, có DR?}
        Object[][] matrix = {
                { 0, 1, false },  // no-report / non-delivery-report
                { 0, 2, true },   // no-report / report
                { 0, 3, true },   // no-report / audited-report
                { 1, 1, false },  // non-delivery-report / non-delivery-report
                { 1, 2, true },   // non-delivery-report / report
                { 1, 3, true },   // non-delivery-report / audited-report
        };

        for (int i = 0; i < matrix.length; i++) {
            reset();
            Object[] r = row(300 + i, "FF", "070430", "METAR VVNB=");
            r[16] = matrix[i][0];
            r[17] = matrix[i][1];

            Gwout g = deliver(r);

            assertPublished(g);
            boolean expectDr = (Boolean) matrix[i][2];
            List<GwoutReport> drs = drsFor(g);
            assertEquals(expectDr, !drs.isEmpty(),
                    String.format("ATS message %d: originator-report-request=%s, "
                                    + "originating-MTA-report-request=%s → %s",
                            i + 1, matrix[i][0], matrix[i][1], expectDr ? "DR" : "không report"));
            if (expectDr) {
                assertEquals("VVHHZTZX", drs.get(0).getRecipient(), "DR gắn với recipient");
            }
            assertTrue(ndrsFor(g).isEmpty(), "bản tin dịch thành công không được sinh NDR");
        }
    }

    // =====================================================================================
    // CTSW004 — NDR khi ATS-message-header sai cú pháp
    // =====================================================================================

    @Test
    @DisplayName("CTSW004: 5 kiểu ATS-message-header hỏng → NDR content-syntax-error")
    void ctsw004_headerSyntaxErrors() {
        Object[][] cases = {
                { "", "070430", "ATS-message-priority rỗng" },
                { "XX", "070430", "ATS-message-priority sai giá trị" },
                { "FF", "", "ATS-message-filing-time rỗng" },
                { "FF", "07043012345", "ATS-message-filing-time sai định dạng" },
                { "", "", "ATS-message-header rỗng hoàn toàn, không có IHE" },
        };

        for (int i = 0; i < cases.length; i++) {
            reset();
            Object[] r = row(400 + i, (String) cases[i][0], (String) cases[i][1], "METAR VVNB=");

            Gwout g = deliver(r);

            assertRejected(g, "content-syntax-error",
                    "unable to convert to AMQP due to ATS-message-header or Heading Fields syntax error");
            assertEquals("ats-header-syntax-error", g.getRejectionReason(), (String) cases[i][2]);
        }
    }

    // =====================================================================================
    // CTSW005 — latest-delivery-time
    // =====================================================================================

    @Test
    @DisplayName("CTSW005: latest-delivery-time đã qua → NDR maximum-time-expired")
    void ctsw005_expiredTtl() {
        Gwout g = deliver(row(500, "FF", "070430", "METAR VVNB="));
        // amss cưỡng chế latest-delivery-time ở tầng MTA nên cột này thường NULL;
        // lưới đỡ phía ITCU vẫn phải hoạt động khi có dữ liệu.
        reset();
        Gwout g2 = deliver(row(501, "FF", "070430", "METAR VVNB="));
        g2.setStatus(OutboundStatus.PENDING.getValue());
        g2.setAmhsTtl(java.time.LocalDateTime.now().minusHours(1));
        publishes.clear();
        reports.clear();

        dispatchService.processOutboundMessage(g2);

        assertEquals(OutboundStatus.FAILED.getValue(), g2.getStatus());
        assertEquals("ttl-expired", g2.getRejectionReason());
        assertEquals("maximum-time-expired", g2.getRejectionDiagnostic());
        List<GwoutReport> ndrs = ndrsFor(g2);
        assertFalse(ndrs.isEmpty(), "phải sinh NDR");
        assertEquals(GwoutReport.REASON_UNABLE_TO_TRANSFER, ndrs.get(0).getReasonCode());
        assertEquals("maximum-time-expired", ndrs.get(0).getDiagnosticCode());
        assertPublished(g); // bản tin thứ nhất (không TTL) vẫn đi bình thường
    }

    @Test
    @DisplayName("CTSW005: latest-delivery-time còn hạn → chuyển đổi bình thường")
    void ctsw005_validTtl() {
        Gwout g = deliver(row(502, "FF", "070430", "METAR VVNB="));
        g.setStatus(OutboundStatus.PENDING.getValue());
        g.setAmhsTtl(java.time.LocalDateTime.now().plusHours(1));
        publishes.clear();

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.TRANSFORMED.getValue(), g.getStatus());
        assertTrue(ndrsFor(g).isEmpty());
    }

    // =====================================================================================
    // CTSW006 — Maximum message data size
    // =====================================================================================

    @Test
    @DisplayName("CTSW006 a): ia5-text dưới ngưỡng → tới được AMQP test interface")
    void ctsw006_underLimit() {
        when(configService.getMaxMsgDataSize()).thenReturn(100);

        Gwout g = deliver(row(600, "FF", "070430", "A".repeat(50)));

        assertPublished(g);
    }

    @Test
    @DisplayName("CTSW006 b): ia5-text vượt ngưỡng → NDR content-too-long cho MỌI recipient")
    void ctsw006_ia5OverLimit() {
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        Object[] base = row(601, "FF", "070430", "A".repeat(200));
        Object[] second = recipientRow(base, "VVCIZTZX");

        Gwout g = deliver(base, second);

        assertRejected(g, "content-too-long", "unable to convert to AMQP due to the content size");
        assertEquals(2, ndrsFor(g).size(), "từ chối cho TẤT CẢ recipient của bản tin");
    }

    @Test
    @DisplayName("CTSW006 c): file-transfer-body-part vượt ngưỡng → đo trên dữ liệu đã giải mã base64")
    void ctsw006_ftbpOverLimit() {
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        ftbpData = new byte[200];
        java.util.Arrays.fill(ftbpData, (byte) 'B');

        Object[] r = row(602, "FF", "070430", "IGNORED");
        r[5] = "403";              // file-transfer-body-part
        r[11] = "attachment.bin";
        r[12] = 200L;

        Gwout g = deliver(r);

        assertRejected(g, "content-too-long", "unable to convert to AMQP due to the content size");
    }

    @Test
    @DisplayName("CTSW006: FTBP sát ngưỡng không bị từ chối nhầm vì phồng base64 (~33%)")
    void ctsw006_ftbpBase64NotInflated() {
        when(configService.getMaxMsgDataSize()).thenReturn(100);
        ftbpData = new byte[90];      // base64 → 120 ký tự, vẫn phải được chấp nhận
        java.util.Arrays.fill(ftbpData, (byte) 'C');

        Object[] r = row(603, "FF", "070430", "IGNORED");
        r[5] = "403";
        r[11] = "attachment.bin";
        r[12] = 90L;

        Gwout g = deliver(r);

        assertPublished(g);
        Publish p = onlyPublish();
        assertNotNull(p.data, "FTBP phải đi bằng phần tử 'data' (BytesMessage)");
        assertEquals(90, p.data.length, "dữ liệu nhị phân phải được giải mã lại đúng kích thước");
        assertNull(p.amqpValue, "FTBP không dùng amqp-value");
    }

    // =====================================================================================
    // CTSW007 — Multiple body parts
    // =====================================================================================

    @Test
    @DisplayName("CTSW007 điện văn 1: ia5-text + FTBP → chấp nhận, amhs_bodypart_type = FTBP")
    void ctsw007_textPlusFtbpAccepted() {
        ftbpData = "BINARY-CONTENT".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Object[] r = row(700, "FF", "070430", "TEXT PART");
        r[5] = "401";
        r[11] = "doc.pdf";
        r[12] = (long) ftbpData.length;
        r[14] = 2;    // hai body part

        Gwout g = deliver(r);

        assertPublished(g);
        assertEquals("file-transfer-body-part", onlyPublish().prop("amhs_bodypart_type"),
                "§4.4.3.4.9: cặp text + FTBP thì amhs_bodypart_type là file-transfer-body-part");
    }

    @Test
    @DisplayName("CTSW007 điện văn 2: general-text + FTBP → chấp nhận")
    void ctsw007_generalTextPlusFtbpAccepted() {
        ftbpData = "BINARY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Object[] r = row(701, "FF", "070430", "TEXT PART");
        r[5] = "402";
        r[10] = "1,6";
        r[11] = "doc.pdf";
        r[12] = (long) ftbpData.length;
        r[14] = 2;

        Gwout g = deliver(r);

        assertPublished(g);
    }

    @Test
    @DisplayName("CTSW007 điện văn 3: hai ia5-text body part → NDR content-syntax-error")
    void ctsw007_twoTextBodyPartsRejected() {
        Object[] r = row(702, "FF", "070430", "TEXT PART");
        r[5] = "401";
        r[14] = 2;    // hai body part nhưng không có FTBP

        Gwout g = deliver(r);

        assertRejected(g, "content-syntax-error",
                "unable to convert to AMQP due to unsupported body part type");
    }

    @Test
    @DisplayName("CTSW007 điện văn 4: ba body part → NDR content-syntax-error")
    void ctsw007_threeBodyPartsRejected() {
        ftbpData = "BINARY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Object[] r = row(703, "FF", "070430", "TEXT PART");
        r[5] = "401";
        r[11] = "doc.pdf";
        r[12] = (long) ftbpData.length;
        r[14] = 3;

        Gwout g = deliver(r);

        assertRejected(g, "content-syntax-error",
                "unable to convert to AMQP due to multiple body parts");
    }

    // =====================================================================================
    // CTSW008 — content-type
    // =====================================================================================

    @Test
    @DisplayName("CTSW008 điện văn 1: content-type interpersonal-messaging-1988(22) → chấp nhận")
    void ctsw008_ipm1988Accepted() {
        Gwout g = deliver(row(800, "FF", "070430", "METAR VVNB="));
        assertPublished(g);
    }

    @Test
    @DisplayName("CTSW008 điện văn 2-4: content-type 2/35/0 → NDR content-type-not-supported")
    void ctsw008_otherContentTypesRejected() {
        int[] unsupported = { 2, 35, 0 };  // 1984, edi-messaging, unidentified
        for (int abstractValue : unsupported) {
            reset();
            Object[] r = row(801 + abstractValue, "FF", "070430", "METAR VVNB=");
            r[18] = abstractValue;

            Gwout g = deliver(r);

            assertRejected(g, "content-type-not-supported", null);
        }
    }

    // =====================================================================================
    // CTSW009 — Distribute to AMHS users and AMQP consumers
    // =====================================================================================

    @Test
    @DisplayName("CTSW009 điện văn 1: 2 primary + 2 copy recipient → 1 publish, amhs_recipients đủ 4")
    void ctsw009_primaryAndCopyRecipients() {
        Object[] p1 = row(900, "FF", "070430", "DISTRIBUTE=");
        p1[21] = Boolean.TRUE;
        Object[] p2 = recipientRow(p1, "VVCIZTZX");    // AMQP consumer
        Object[] c1 = recipientRow(p1, "VVDNZTZX");    // copy recipient
        Object[] c2 = recipientRow(p1, "VVPQZTZX");    // copy recipient

        Gwout g = deliver(p1, p2, c1, c2);

        assertPublished(g);
        Publish p = onlyPublish();
        assertEquals("VVHHZTZX,VVCIZTZX,VVDNZTZX,VVPQZTZX", p.prop("amhs_recipients"),
                "§4.4.3.4.4: CC/BCC xử lý như primary, cùng nằm trong amhs_recipients");
        assertEquals(4, dispatches.size(), "mỗi recipient một dòng dispatch");
    }

    @Test
    @DisplayName("CTSW009 điện văn 2: chỉ recipient blind-copy có mặt trong MTE → chỉ họ được chuyển")
    void ctsw009_blindCopyRecipientsOnly() {
        Object[] bcc1 = row(901, "FF", "070430", "DISTRIBUTE BCC=");
        bcc1[9] = orName("VVCIZTZX");
        bcc1[21] = Boolean.TRUE;
        Object[] bcc2 = recipientRow(bcc1, "VVPQZTZX");

        Gwout g = deliver(bcc1, bcc2);

        assertPublished(g);
        assertEquals("VVCIZTZX,VVPQZTZX", onlyPublish().prop("amhs_recipients"));
    }

    // =====================================================================================
    // CTSW010 — Maximum message number of recipients
    // =====================================================================================

    @Test
    @DisplayName("CTSW010 a): 512 recipient (đúng ngưỡng) → chuyển đổi thành công")
    void ctsw010_atLimitAccepted() {
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        Gwout g = deliver(recipientRows(1000, 512));
        assertPublished(g);
        assertEquals(512, onlyPublish().prop("amhs_recipients").split(",").length);
    }

    @Test
    @DisplayName("CTSW010 b): 513 recipient → NDR too-many-recipients cho TOÀN BỘ recipient")
    void ctsw010_overLimitRejected() {
        when(configService.getMaxMsgRecipients()).thenReturn(512);

        Gwout g = deliver(recipientRows(1001, 513));

        assertRejected(g, "too-many-recipients",
                "unable to convert to AMQP due to number of recipients");
        assertEquals(513, ndrsFor(g).size(), "NDR cho tất cả recipient");
    }

    /** Sinh N dòng mtcu_to với địa chỉ AFTN 8 chữ cái khác nhau. */
    private List<Object[]> recipientRows(long id, int count) {
        Object[] base = row(id, "FF", "070430", "MANY RECIPIENTS=");
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(recipientRow(base, aftn(i)));
        }
        return rows;
    }

    private static String aftn(int i) {
        // 4 chữ cái cố định + 4 chữ cái sinh từ chỉ số → luôn khớp [A-Z]{8}, không trùng nhau
        StringBuilder sb = new StringBuilder("VVTS");
        int n = i;
        for (int k = 0; k < 4; k++) {
            sb.append((char) ('A' + (n % 26)));
            n /= 26;
        }
        return sb.toString();
    }

    // =====================================================================================
    // CTSW011 / CTSW012 / CTSW013 — Probes
    // =====================================================================================

    /** Probe do amss ghi thẳng vào gwout (body_type='probe'), không đi qua mtcu_tmp. */
    private Gwout probe(long id, String originator, String recipients, Integer contentLength) {
        Gwout g = new Gwout();
        g.setMsgid(id);
        g.setAmhsid("PROBE-" + id);
        g.setBodyType("probe");
        g.setOrigin(originator);
        g.setAddress(recipients);
        g.setContentLength(contentLength);
        g.setStatus(OutboundStatus.PENDING.getValue());
        gwouts.add(g);
        return g;
    }

    @Test
    @DisplayName("CTSW011 Probe 1: content-length dưới ngưỡng, recipient hợp lệ → DR")
    void ctsw011_probe1_dr() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        Gwout g = probe(1100, "VVNBZTZX", "VVHHZTZX", 1024);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), g.getStatus());
        assertEquals(1, drsFor(g).size(), "Probe 1 phải nhận DR");
        assertTrue(ndrsFor(g).isEmpty());
    }

    @Test
    @DisplayName("CTSW011 Probe 2: recipient không chuyển được sang AF-address → NDR")
    void ctsw011_probe2_ndr() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        Gwout g = probe(1101, "VVNBZTZX", "BAD1", 1024);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.FAILED.getValue(), g.getStatus());
        List<GwoutReport> ndrs = ndrsFor(g);
        assertEquals(1, ndrs.size());
        assertEquals("unrecognised-OR-name", ndrs.get(0).getDiagnosticCode());
    }

    @Test
    @DisplayName("CTSW011 Probe 3: content-length vượt Maximum message data size → NDR content-too-long")
    void ctsw011_probe3_ndr() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        Gwout g = probe(1102, "VVNBZTZX", "VVHHZTZX", 5000);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.FAILED.getValue(), g.getStatus());
        List<GwoutReport> ndrs = ndrsFor(g);
        assertEquals(1, ndrs.size());
        assertEquals("content-too-long", ndrs.get(0).getDiagnosticCode());
        assertEquals("unable to convert to AMQP due to the content size", ndrs.get(0).getSupplementaryInfo());
    }

    @Test
    @DisplayName("CTSW011 Probe 4: đúng 512 recipient → DR")
    void ctsw011_probe4_dr() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 512; i++) {
            if (i > 0) sb.append(',');
            sb.append(aftn(i));
        }
        Gwout g = probe(1103, "VVNBZTZX", sb.toString(), 1024);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.PUBLISHED.getValue(), g.getStatus());
        assertEquals(512, drsFor(g).size(), "mỗi recipient một dòng DR");
        assertTrue(ndrsFor(g).isEmpty());
    }

    @Test
    @DisplayName("CTSW011 Probe 5: vượt 512 recipient → NDR too-many-recipients")
    void ctsw011_probe5_ndr() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        when(configService.getMaxMsgRecipients()).thenReturn(512);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 513; i++) {
            if (i > 0) sb.append(',');
            sb.append(aftn(i));
        }
        Gwout g = probe(1104, "VVNBZTZX", sb.toString(), 1024);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.FAILED.getValue(), g.getStatus());
        assertEquals("too-many-recipients", ndrsFor(g).get(0).getDiagnosticCode());
    }

    @Test
    @DisplayName("CTSW012: Probe 2 recipient, 1 không tra được → combined report (DR + NDR)")
    void ctsw012_combinedReport() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        Gwout g = probe(1200, "VVNBZTZX", "VVHHZTZX,BADADDR1", 1024);

        dispatchService.processOutboundMessage(g);

        List<GwoutReport> drs = drsFor(g);
        List<GwoutReport> ndrs = ndrsFor(g);
        assertEquals(1, drs.size(), "recipient hợp lệ nhận DR");
        assertEquals("VVHHZTZX", drs.get(0).getRecipient());
        assertEquals(1, ndrs.size(), "recipient không xác định nhận NDR");
        assertEquals("BADADDR1", ndrs.get(0).getRecipient());
        assertEquals("unrecognised-OR-name", ndrs.get(0).getDiagnosticCode());
    }

    @Test
    @DisplayName("CTSW013: Probe có originator không tra được → NDR invalid-arguments cho MỌI recipient")
    void ctsw013_unknownOriginator() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);
        when(authorizationService.isAmhsUserAuthorized("VVXXZTZX")).thenReturn(false);
        Gwout g = probe(1300, "VVXXZTZX", "VVHHZTZX,VVCIZTZX", 1024);

        dispatchService.processOutboundMessage(g);

        assertEquals(OutboundStatus.FAILED.getValue(), g.getStatus());
        List<GwoutReport> ndrs = ndrsFor(g);
        assertEquals(2, ndrs.size(), "NDR cho tất cả recipient");
        for (GwoutReport ndr : ndrs) {
            assertEquals(GwoutReport.REASON_UNABLE_TO_TRANSFER, ndr.getReasonCode());
            assertEquals("invalid-arguments", ndr.getDiagnosticCode());
            assertEquals("unable to convert to AMQP due to unrecognized originator O/R address",
                    ndr.getSupplementaryInfo());
        }
        assertTrue(drsFor(g).isEmpty());
    }

    // =====================================================================================
    // CTSW016 — current encoded-information-types
    // =====================================================================================

    @Test
    @DisplayName("CTSW016: 12 giá trị EIT — hợp lệ thì chuyển đổi, không hợp lệ thì NDR")
    void ctsw016_eitMatrix() {
        Object[][] cases = {
                { "ia5-text", true, "1. built-in ia5-text" },
                { "unspecified", true, "2. built-in unspecified" },
                { "{id-eit-ia5-text}", true, "3. extended ia5-text" },
                { "{id-eit-unknown 0}", true, "4. extended unknown" },
                { "{id-cs-eit-authority 1}", true, "5. authority 1" },
                { "{id-cs-eit-authority 2}", true, "6. authority 2" },
                { "{id-cs-eit-authority 1},{id-cs-eit-authority 6}", true, "7. authority 1 + 6" },
                { "{id-cs-eit-authority 1},{id-cs-eit-authority 6},{id-cs-eit-authority 100}",
                        true, "8. authority 1 + 6 + 100" },
                { "{id-cs-eit-authority 3}", false, "9. authority 3 không hợp lệ" },
                { "{id-cs-eit-authority 1},{id-cs-eit-authority 6},{id-cs-eit-authority 7}",
                        false, "10. lẫn authority 7 không hợp lệ" },
                { "ia5-text,{id-cs-eit-authority 1},{id-cs-eit-authority 6}", true,
                        "11. built-in ia5-text + authority 1 + 6" },
                { "{id-eit-file-transfer 0}", true, "12. file-transfer" },
        };

        for (int i = 0; i < cases.length; i++) {
            reset();
            Object[] r = row(1600 + i, "FF", "070430", "METAR VVNB=");
            r[15] = cases[i][0];
            if (((String) cases[i][0]).contains("file-transfer")) {
                r[5] = "403";
                ftbpData = "BIN".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                r[11] = "f.bin";
                r[12] = 3L;
            }

            Gwout g = deliver(r);

            if ((Boolean) cases[i][1]) {
                assertPublished(g);
            } else {
                assertRejected(g, "encoded-information-types-unsupported", null);
            }
        }
    }

    @Test
    @DisplayName("CTSW016: Probe với EIT không hợp lệ → NDR; EIT hợp lệ → DR")
    void ctsw016_probeEit() {
        when(configService.getMaxMsgDataSize()).thenReturn(2048);

        Gwout bad = probe(1620, "VVNBZTZX", "VVHHZTZX", 1024);
        bad.setOriginEit("{id-cs-eit-authority 3}");
        dispatchService.processOutboundMessage(bad);
        assertEquals(OutboundStatus.FAILED.getValue(), bad.getStatus());
        assertEquals("encoded-information-types-unsupported", ndrsFor(bad).get(0).getDiagnosticCode());

        Gwout ok = probe(1621, "VVNBZTZX", "VVHHZTZX", 1024);
        ok.setOriginEit("{id-cs-eit-authority 1}");
        dispatchService.processOutboundMessage(ok);
        assertEquals(OutboundStatus.PUBLISHED.getValue(), ok.getStatus());
        assertEquals(1, drsFor(ok).size());
    }

    // =====================================================================================
    // CTSW017 — ia5-text-body-part
    // =====================================================================================

    @Test
    @DisplayName("CTSW017 điện văn 1-2: ia5-text-body-part (EIT mở rộng và built-in) → chấp nhận, "
            + "amhs_content_encoding = IA5")
    void ctsw017_ia5TextAccepted() {
        String[] eits = { "{id-eit-ia5-text}", "ia5-text" };
        for (int i = 0; i < eits.length; i++) {
            reset();
            Object[] r = row(1700 + i, "FF", "070430", "ATS MESSAGE TEXT IA5");
            r[5] = "401";
            r[15] = eits[i];

            Gwout g = deliver(r);

            assertPublished(g);
            Publish p = onlyPublish();
            assertEquals("ia5-text-body-part", p.prop("amhs_bodypart_type"), "Table 6");
            assertEquals("IA5", p.prop("amhs_content_encoding"), "§4.4.3.4.9");
        }
    }

    @Test
    @DisplayName("CTSW017 điện văn 3: repertoire ita2(2) → NDR content-syntax-error / unsupported body part type")
    void ctsw017_ita2Rejected() {
        Object[] r = row(1702, "FF", "070430", "ATS MESSAGE TEXT");
        r[5] = "401";
        r[10] = "ITA2";
        r[15] = "{id-eit-ia5-text}";

        Gwout g = deliver(r);

        assertRejected(g, "content-syntax-error",
                "unable to convert to AMQP due to unsupported body part type");
    }

    // =====================================================================================
    // CTSW018 — general-text-body-part, ISO 646
    // =====================================================================================

    @Test
    @DisplayName("CTSW018: general-text-body-part với character set 1,6 (ISO 646) → chấp nhận")
    void ctsw018_iso646Accepted() {
        String[] texts = {
                "ATS MESSAGE TEXT ASCII ONLY",
                "ATS MESSAGE TEXT ASCII AND ÉÑ NON-LISTED",
        };
        for (int i = 0; i < texts.length; i++) {
            reset();
            Object[] r = row(1800 + i, "FF", "070430", texts[i]);
            r[5] = "402";
            r[10] = "1,6";
            r[15] = "{id-cs-eit-authority 1},{id-cs-eit-authority 6}";

            Gwout g = deliver(r);

            assertPublished(g);
            Publish p = onlyPublish();
            assertEquals("general-text-body-part", p.prop("amhs_bodypart_type"), "Table 6");
            assertEquals("ISO-646", p.prop("amhs_content_encoding"), "§4.4.3.4.9");
            assertEquals(texts[i], p.amqpValue, "nội dung phải giữ nguyên");
        }
    }

    // =====================================================================================
    // CTSW019 — general-text-body-part, repertoire khác ISO 646
    // =====================================================================================

    @Test
    @DisplayName("CTSW019: chính sách nội bộ TỪ CHỐI → NDR content-syntax-error / "
            + "unsupported encoded-information-types")
    void ctsw019_rejectedByLocalPolicy() {
        when(configService.isNonIso646RepertoireAllowed()).thenReturn(false);
        String[][] cases = {
                { "1,6,100", "1. ISO 8859-1" },
                { "1,6,144", "2. Cyrillic" },
                { "1,6,58", "3. CJK" },
        };
        for (int i = 0; i < cases.length; i++) {
            reset();
            Object[] r = row(1900 + i, "FF", "070430", "TEXT WITH ÉÑ CHARACTERS");
            r[5] = "402";
            r[10] = cases[i][0];
            r[15] = "{id-cs-eit-authority 1},{id-cs-eit-authority 6},{id-cs-eit-authority 100}";

            Gwout g = deliver(r);

            assertRejected(g, "content-syntax-error",
                    "unable to convert to AMQP due to unsupported encoded-information-types");
        }
    }

    @Test
    @DisplayName("CTSW019: chính sách nội bộ CHO PHÉP → chuyển đổi, amhs_content_encoding = ISO-8859-1")
    void ctsw019_allowedByLocalPolicy() {
        when(configService.isNonIso646RepertoireAllowed()).thenReturn(true);
        Object[] r = row(1910, "FF", "070430", "TEXT WITH ÉÑ CHARACTERS");
        r[5] = "402";
        r[10] = "1,6,100";
        r[15] = "{id-cs-eit-authority 1},{id-cs-eit-authority 6},{id-cs-eit-authority 100}";

        Gwout g = deliver(r);

        assertPublished(g);
        assertEquals("ISO-8859-1", onlyPublish().prop("amhs_content_encoding"), "Table 6");
    }

    // =====================================================================================
    // CTSW020 — Notify SS to Control Position
    // =====================================================================================

    @Test
    @DisplayName("CTSW020 điện văn 1: responsible, precedence cao nhất 107 → báo CP và vẫn chuyển đổi")
    void ctsw020_message1_precedence107() {
        Object[] r1 = row(2000, "KK", "070430", "SS MESSAGE=");
        r1[20] = 107;
        r1[21] = Boolean.TRUE;
        Object[] r2 = recipientRow(r1, "VVCIZTZX");
        r2[20] = 28;

        Gwout g = deliver(r1, r2);

        assertPublished(g);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("precedence 107")),
                "§4.4.4.4: phải báo Control Position");
        assertEquals("SS", onlyPublish().prop("amhs_ats_pri"));
    }

    @Test
    @DisplayName("CTSW020 điện văn 2: responsible, ATS-message-priority SS (Basic IPM) → báo CP")
    void ctsw020_message2_basicSs() {
        Object[] r1 = row(2001, "SS", "070430", "SS BASIC=");
        r1[21] = Boolean.TRUE;
        Object[] r2 = recipientRow(r1, "VVCIZTZX");

        Gwout g = deliver(r1, r2);

        assertPublished(g);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("ATS-message-priority SS")),
                "§4.4.4.4: Basic IPM ưu tiên SS phải báo Control Position");
    }

    @Test
    @DisplayName("CTSW020 điện văn 3: 1 responsible + 1 not-responsible, cả hai precedence 107 → "
            + "báo CP, chỉ recipient responsible được chuyển")
    void ctsw020_message3_mixedResponsibility() {
        Object[] r1 = row(2002, "KK", "070430", "SS MIXED=");
        r1[20] = 107;
        r1[21] = Boolean.TRUE;
        Object[] r2 = recipientRow(r1, "VVCIZTZX");
        r2[20] = 107;
        r2[21] = Boolean.FALSE;

        Gwout g = deliver(r1, r2);

        assertPublished(g);
        assertTrue(alerts.stream().anyMatch(a -> a.contains("precedence 107")));
        assertEquals("VVHHZTZX", onlyPublish().prop("amhs_recipients"),
                "§4.4.3.4.4: chỉ recipient 'responsible' nằm trong amhs_recipients");
    }

    @Test
    @DisplayName("CTSW020 điện văn 4: precedence 14 → KHÔNG báo CP")
    void ctsw020_message4_noAlert() {
        Object[] r1 = row(2003, "KK", "070430", "NORMAL=");
        r1[20] = 14;
        r1[21] = Boolean.TRUE;
        Object[] r2 = recipientRow(r1, "VVCIZTZX");
        r2[20] = 14;

        Gwout g = deliver(r1, r2);

        assertPublished(g);
        assertTrue(alerts.isEmpty(), "precedence 14 không phải tình huống báo Control Position");
    }

    @Test
    @DisplayName("CTSW020 điện văn 5: ATS-message-priority FF → KHÔNG báo CP")
    void ctsw020_message5_noAlert() {
        Object[] r1 = row(2004, "FF", "070430", "NORMAL BASIC=");
        r1[21] = Boolean.TRUE;
        Object[] r2 = recipientRow(r1, "VVCIZTZX");

        Gwout g = deliver(r1, r2);

        assertPublished(g);
        assertTrue(alerts.isEmpty(), "ưu tiên FF không phải tình huống báo Control Position");
    }

    // =====================================================================================
    // CTSW014 / CTSW015 — Incoming RN (§4.4.7)
    // =====================================================================================

    private vn.asg.swim.service.AmhsFeedbackService feedbackService(
            vn.asg.swim.repository.GwinRepository gwinRepository) {
        return new vn.asg.swim.service.AmhsFeedbackService(
                mock(vn.asg.swim.repository.AmhsFeedbackRepository.class),
                gwinRepository, gwoutRepository, reportService, alertService, conversionService);
    }

    private vn.asg.swim.model.AmhsFeedback rn(long id, String subjectIpm) {
        vn.asg.swim.model.AmhsFeedback f = new vn.asg.swim.model.AmhsFeedback();
        f.setId(id);
        f.setIpnType(vn.asg.swim.model.AmhsFeedback.TYPE_RN);
        f.setSubjectIpm(subjectIpm);
        f.setSubjectMts("MTS-" + subjectIpm);
        f.setOrigin("VVHHZTZX");
        f.setRecipient("VVNBZTZX");
        return f;
    }

    private vn.asg.swim.entity.Gwin subjectMessage(long msgid, String ipmId, String atsPriority) {
        vn.asg.swim.entity.Gwin g = new vn.asg.swim.entity.Gwin();
        g.setMsgid(msgid);
        g.setIpmId(ipmId);
        g.setMtsId("MTS-" + ipmId);
        g.setMessageId("AMQP-" + ipmId);
        g.setAtsPriority(atsPriority);
        g.setOrigin("VVNBZTZX");
        return g;
    }

    @Test
    @DisplayName("CTSW014 điện văn 1: RN có điện văn gốc ưu tiên SS → không bị từ chối, "
            + "được log và báo Control Position")
    void ctsw014_rnForSsSubject() {
        var gwinRepository = mock(vn.asg.swim.repository.GwinRepository.class);
        var subject = subjectMessage(10L, "IPM-SS-1", "SS");
        when(gwinRepository.findByIpmId("IPM-SS-1")).thenReturn(List.of(subject));
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());

        feedbackService(gwinRepository).processFeedback(rn(1, "IPM-SS-1"));

        assertTrue(reports.isEmpty(), "§4.4.7.2: RN hợp lệ không sinh NDR");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("§4.4.7.3")),
                "phải báo Control Position để lưu trữ và xử lý");
    }

    @Test
    @DisplayName("CTSW014 điện văn 2: RN có điện văn gốc ưu tiên khác SS → từ chối, log và báo CP")
    void ctsw014_rnForNonSsSubject() {
        var gwinRepository = mock(vn.asg.swim.repository.GwinRepository.class);
        var subject = subjectMessage(11L, "IPM-FF-1", "FF");
        when(gwinRepository.findByIpmId("IPM-FF-1")).thenReturn(List.of(subject));
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());

        feedbackService(gwinRepository).processFeedback(rn(2, "IPM-FF-1"));

        assertTrue(alerts.stream().anyMatch(a -> a.contains("§4.4.7.2") && a.contains("khác SS")),
                "§4.4.7.2: phải log lỗi và báo Control Position");
        assertTrue(reports.isEmpty(),
                "§4.4.7.2 chỉ yêu cầu log + báo CP, không yêu cầu NDR cho nhánh này");
    }

    @Test
    @DisplayName("CTSW015: RN có điện văn gốc bịa → NDR invalid-arguments + "
            + "supplementary 'unable to notify RN to SWIM due to misrouted RN'")
    void ctsw015_misroutedRn() {
        var gwinRepository = mock(vn.asg.swim.repository.GwinRepository.class);
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of());
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());

        feedbackService(gwinRepository).processFeedback(rn(3, "IPM-FICTITIOUS"));

        assertEquals(1, reports.size(), "phải sinh đúng một NDR");
        GwoutReport ndr = reports.get(0);
        assertEquals(GwoutReport.TYPE_NDR, ndr.getReportType());
        assertEquals(GwoutReport.REASON_UNABLE_TO_TRANSFER, ndr.getReasonCode(),
                "non-delivery-reason-code");
        assertEquals("invalid-arguments", ndr.getDiagnosticCode(), "non-delivery-diagnostic-code");
        assertEquals("unable to notify RN to SWIM due to misrouted RN", ndr.getSupplementaryInfo(),
                "supplementary-information");
        assertEquals("MTS-IPM-FICTITIOUS", ndr.getMtsId(),
                "MTS-Identifier của điện văn gốc phải xuống tới amss để dựng NDR");
        assertTrue(alerts.stream().anyMatch(a -> a.contains("§4.4.7.1")),
                "phải báo Control Position và lưu RN lại để xử lý");
    }

    // =====================================================================================
    // CTSW016 với ĐỊNH DẠNG EIT THẬT của AMHS Component
    //
    // Các test CTSW016 phía trên dùng dạng ký hiệu "{id-cs-eit-authority N}". Dữ liệu thật trong
    // mtcu_tmp.originEncodeInformationType lại là OID DẠNG SỐ, ngăn cách bằng DẤU CÁCH:
    //     1.0.10021.7.1.0.N          — {id-cs-eit-authority N} theo arc ICAO/ATN
    //     2.16.840.1.101.2.1.22.N    — {id-cs-eit-authority N} theo arc còn lại
    //     2.6.1.12.0                 — {id-eit-file-transfer 0}
    // =====================================================================================

    @Nested
    @DisplayName("CTSW016 — EIT ở định dạng OID thật của AMHS Component")
    class Ctsw016RealOidFormat {

        private Gwout withEit(long id, String eit) {
            Object[] r = row(id, "FF", "070430", "METAR VVNB=");
            r[15] = eit;
            return deliver(r);
        }

        @Test
        @DisplayName("{id-cs-eit-authority 1/2/6/100} dạng OID ICAO/ATN phải được chấp nhận (§4.4.2.1a)")
        void icaoAuthorityOidsAccepted() {
            String[] eits = {
                    "1.0.10021.7.1.0.1",                                        // authority 1
                    "1.0.10021.7.1.0.2",                                        // authority 2
                    "1.0.10021.7.1.0.100",                                      // authority 100
                    "1.0.10021.7.1.0.6 1.0.10021.7.1.0.1",                      // authority 6 + 1
                    "1.0.10021.7.1.0.100 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1",  // 100 + 6 + 1
            };
            for (int i = 0; i < eits.length; i++) {
                reset();
                Gwout g = withEit(1650 + i, eits[i]);
                assertPublished(g);
            }
        }

        @Test
        @DisplayName("{id-cs-eit-authority N} ở arc 2.16.840.1.101.2.1.22 — 1/2/6/100 nhận, 3 từ chối")
        void otherAuthorityArc() {
            String[] valid = {
                    "2.16.840.1.101.2.1.22.1",
                    "2.16.840.1.101.2.1.22.2",
                    "2.16.840.1.101.2.1.22.6",
                    "2.16.840.1.101.2.1.22.6 2.16.840.1.101.2.1.22.1",
                    "2.16.840.1.101.2.1.22.100 2.16.840.1.101.2.1.22.6 2.16.840.1.101.2.1.22.1",
            };
            for (int i = 0; i < valid.length; i++) {
                reset();
                assertPublished(withEit(1660 + i, valid[i]));
            }

            reset();
            assertRejected(withEit(1670, "2.16.840.1.101.2.1.22.3"),
                    "encoded-information-types-unsupported", null);
        }

        @Test
        @DisplayName("{id-eit-file-transfer 0} = OID 2.6.1.12.0 phải được chấp nhận (§4.4.2.1a)")
        void fileTransferOidAccepted() {
            assertPublished(withEit(1680, "2.6.1.12.0"));
        }

        @Test
        @DisplayName("Danh sách ngăn cách bằng DẤU CÁCH phải được tách ra từng giá trị: "
                + "một giá trị sai là từ chối cả bản tin (§4.4.2.1b)")
        void spaceSeparatedListMustBeSplit() {
            // Chuỗi bắt đầu bằng "ia5-text" (hợp lệ) nhưng kèm authority 3 (KHÔNG hợp lệ).
            // Nếu bộ kiểm tra không tách theo dấu cách, cả chuỗi được coi là một token chứa
            // "ia5-text" và bản tin lọt qua — đây là chấp nhận NHẦM, nguy hiểm hơn từ chối nhầm.
            Gwout g = withEit(1690, "ia5-text 2.16.840.1.101.2.1.22.3");

            assertRejected(g, "encoded-information-types-unsupported", null);
        }

        @Test
        @DisplayName("ia5-text kèm các authority hợp lệ, ngăn cách bằng dấu cách → chấp nhận")
        void ia5TextWithValidAuthorities() {
            assertPublished(withEit(1691, "ia5-text 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1"));
            reset();
            assertPublished(withEit(1692, "ia5-text 2.6.1.12.0"));
        }
    }

    /**
     * Hồi quy trên TOÀN BỘ giá trị EIT có thật: 20 giá trị phân biệt lấy từ
     * {@code SELECT DISTINCT originEncodeInformationType FROM mtcu_tmp} ngày 08/09/2026.
     * <p>
     * Trước bản sửa, 42/45 bản tin {@code gwout} trạng thái FAILED mang lý do
     * {@code unsupported-eit} - phần lớn là từ chối oan.
     */
    @Nested
    @DisplayName("CTSW016 — hồi quy trên dữ liệu EIT thật của hệ thống")
    class Ctsw016ProductionData {

        private void accept(String eit) {
            assertTrue(validationService.validateEncodedInformationTypes(eit).isValid(),
                    "phải chấp nhận EIT: " + eit);
        }

        private void reject(String eit) {
            assertFalse(validationService.validateEncodedInformationTypes(eit).isValid(),
                    "phải từ chối EIT: " + eit);
        }

        @Test
        @DisplayName("18 giá trị hợp lệ đang chạy trên đường truyền đều phải được chấp nhận")
        void allValidProductionValues() {
            accept(null);                       // 25 bản tin không khai EIT
            accept("ia5-text");
            accept("2.6.3.4.2");
            accept("2.6.1.12.0");
            accept("1.0.10021.7.1.0.1");
            accept("1.0.10021.7.1.0.2");
            accept("1.0.10021.7.1.0.100");
            accept("2.16.840.1.101.2.1.22.1");
            accept("2.16.840.1.101.2.1.22.2");
            accept("2.16.840.1.101.2.1.22.6");
            accept("ia5-text 2.6.1.12.0");
            accept("1.0.10021.7.1.0.6 1.0.10021.7.1.0.1");
            accept("ia5-text 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1");
            accept("2.16.840.1.101.2.1.22.6 2.16.840.1.101.2.1.22.1");
            accept("1.0.10021.7.1.0.100 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1");
            accept("ia5-text 2.6.1.12.0 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1");
            accept("ia5-text 1.0.10021.7.1.0.6 1.0.10021.7.1.0.1 2.6.3.4.2");
            accept("2.16.840.1.101.2.1.22.100 2.16.840.1.101.2.1.22.6 2.16.840.1.101.2.1.22.1");
            accept("ia5-text 2.16.840.1.101.2.1.22.6 2.16.840.1.101.2.1.22.1 2.6.3.4.2");
        }

        @Test
        @DisplayName("Giá trị không hợp lệ duy nhất trên đường truyền vẫn phải bị từ chối")
        void invalidProductionValueStaysRejected() {
            reject("2.16.840.1.101.2.1.22.3");   // {id-cs-eit-authority 3}
        }

        @Test
        @DisplayName("Bản sửa không nới lỏng: authority ngoài {1,2,6,100} và OID lạ vẫn bị từ chối")
        void stillRejectsUnknownValues() {
            reject("1.0.10021.7.1.0.3");                          // authority 3, arc ICAO
            reject("1.0.10021.7.1.0.7");                          // authority 7
            reject("2.6.3.4.5");                                  // built-in teletex
            reject("1.2.3.4.5");                                  // OID không thuộc arc nào
            reject("ia5-text 1.0.10021.7.1.0.3");                 // giá trị cấm nấp sau giá trị hợp lệ
            reject("1.0.10021.7.1.0.1 2.16.840.1.101.2.1.22.3");  // trộn hai arc, một giá trị cấm
            reject("1.0.10021.7.1.0.1.5");                        // arc thừa, không phải authority N
        }
    }
}
