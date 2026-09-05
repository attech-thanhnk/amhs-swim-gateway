package vn.asg.swim.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.OutboundStatus;
import vn.asg.swim.repository.GwoutRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmhsToGwoutSyncSchedulerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Mock
    private GwoutRepository gwoutRepository;


    @Mock
    private vn.asg.swim.service.AlertService alertService;

    private AmhsToGwoutSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AmhsToGwoutSyncScheduler(entityManager, gwoutRepository, alertService);
    }

    @Test
    void testSyncAmhsToGwout_NoNewMessages() {
        // Given: Empty result list
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(new ArrayList<>());

        // When
        scheduler.syncAmhsToGwout();

        // Then: Should not sync or save anything
        verify(gwoutRepository, never()).saveAndFlush(any(Gwout.class));
    }

    @Test
    void testSyncAmhsToGwout_WithNewMessages() {
        // Given: A mock row from mtcu_tmp/mtcu_to
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[14];
        row[0] = 59293L; // id
        row[1] = "METAR VVCI 070130Z 21005KT 150V250 9999 BKN019 31/26 Q1002 NOSIG="; // content
        row[2] = "070130"; // filingTime
        row[3] = "FF"; // atsPriority
        row[4] = "OHI-123"; // atsOhi
        row[5] = "401"; // bodyPartType
        row[6] = "IPM-123"; // ipmId
        row[7] = "MSG-123"; // messageId
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/"; // orAddress
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/"; // recipient_address
        row[10] = "ISO-8859-1"; // bodyPartCharacterSet
        row[11] = null; // ftbpFileName
        row[12] = null; // ftbpObjectSize
        row[13] = null; // ftbpLastMod
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then: Verify saved gwout details
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("MSG-123", gwout.getAmhsid());
            assertEquals("VVCIYMYX", gwout.getOrigin());
            assertEquals("VVTSSWIM", gwout.getAddress());
            assertEquals("070130", gwout.getFilingTime());
            assertEquals("OHI-123", gwout.getOptionalHeading());
            assertEquals("FF", gwout.getAmhsPriority());
            assertEquals(4, gwout.getSwimPriority()); // FF -> AMQP priority 4 (EUR Doc 047 v3.0 Table 3)
            assertEquals(OutboundStatus.PENDING.getValue(), gwout.getStatus());
            assertEquals("text", gwout.getBodyType());
            return true;
        }));
    }

    @Test
    void testSyncAmhsToGwout_MultipleRecipients_ShouldJoinAllRecipients() {
        // Given: 1 IPM (mtcu_tmp.id=59296) có 3 dòng mtcu_to - cả ba đều là recipient của IPM
        List<Object[]> mockRows = new ArrayList<>();

        Object[] rowRecipient0 = new Object[14];
        rowRecipient0[0] = 59296L;
        rowRecipient0[1] = "METAR VVCI 070130Z 21005KT 150V250 9999 BKN019 31/26 Q1002 NOSIG=";
        rowRecipient0[2] = "070130";
        rowRecipient0[3] = "FF";
        rowRecipient0[4] = null;
        rowRecipient0[5] = "401";
        rowRecipient0[6] = "IPM-126";
        rowRecipient0[7] = "MSG-126";
        rowRecipient0[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        rowRecipient0[9] = "/CN=VVTSOPTB/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        rowRecipient0[10] = "ISO-8859-1";
        mockRows.add(rowRecipient0);

        Object[] rowRecipient1 = rowRecipient0.clone();
        rowRecipient1[9] = "/CN=VVCIZTZX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        mockRows.add(rowRecipient1);

        Object[] rowRecipient2 = rowRecipient0.clone();
        rowRecipient2[9] = "/CN=VVHHZTZX/OU=VVHH/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        mockRows.add(rowRecipient2);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then: chỉ 1 gwout được tạo (gộp theo mtcu_tmp.id), amhs_recipients liệt kê đủ 3 recipient (§4.4.3.4.4)
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("MSG-126", gwout.getAmhsid());
            assertEquals("VVTSOPTB,VVCIZTZX,VVHHZTZX", gwout.getAddress());
            return true;
        }));
    }

    @Test
    void testSyncAmhsToGwout_GeneralTextBodyPart_ShouldMapCharset() {
        // Given: bodyPartType=402 (general-text) với bodyPartCharacterSet=ISO-8859-1
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[14];
        row[0] = 59294L;
        row[1] = "GENERAL TEXT MESSAGE";
        row[2] = "070130";
        row[3] = "GG";
        row[4] = null;
        row[5] = "402"; // bodyPartType -> general-text-body-part
        row[6] = null;
        row[7] = "MSG-124";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[10] = "ISO-8859-1"; // bodyPartCharacterSet
        row[11] = null; // ftbpFileName
        row[12] = null; // ftbpObjectSize
        row[13] = null; // ftbpLastMod
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("general-text-body-part", gwout.getBodyPartType());
            assertEquals("ISO-8859-1", gwout.getBodyPartCharset());
            return true;
        }));
    }

    @Test
    void testSyncAmhsToGwout_FileTransferBodyPart_ShouldMapFtbpAttributes() {
        // Given: bodyPartType không rơi vào 401/402 -> file-transfer-body-part, kèm FTBP file-attributes
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[14];
        row[0] = 59295L;
        row[1] = "QkFTRTY0Q09OVEVOVA=="; // content (base64 giả lập)
        row[2] = "070130";
        row[3] = "KK";
        row[4] = null;
        row[5] = "403"; // bodyPartType -> file-transfer-body-part
        row[6] = null;
        row[7] = "MSG-125";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[10] = null; // bodyPartCharacterSet - không áp dụng cho ftbp
        row[11] = "report.pdf"; // file_name (server 188)
        row[12] = 204800L;      // OCTET_LENGTH(data)
        row[13] = null;         // không còn nguồn cho ftbpLastMod
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("file-transfer-body-part", gwout.getBodyPartType());
            assertEquals("ftbp", gwout.getBodyType());
            assertEquals("report.pdf", gwout.getFtbpFileName());
            // Kích thước suy từ OCTET_LENGTH(data), không phải cột riêng
            assertEquals("204800", gwout.getFtbpObjectSize());
            // §4.4.3.4.2: ba thuộc tính FTBP đều optional. Không server nào có nguồn cho
            // date-and-time-of-last-modification nên property này vắng mặt - hợp lệ.
            assertEquals(null, gwout.getFtbpLastMod());
            return true;
        }));
    }

    @Test
    void testSyncAmhsToGwout_TextPlusFtbp_ShouldBecomeFileTransferBodyPart() {
        // §4.4.3.4.9: "Upon reception of a message with two body parts, one file-transfer-body
        // part and one text body part, the amhs_bodypart_type shall contain the value
        // file-transfer-body part." CTSW007 điện văn 1-2.
        // Server 188 báo bodyPartType = 401 (theo phần text) nhưng có data -> phải thành FTBP.
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[22];
        row[0] = 59299L;
        row[1] = "ZCZC ABC001 FF VVHHZTZX";  // phần text = ATS message header
        row[2] = "070130";
        row[3] = "FF";
        row[5] = "401";                       // 188 báo theo body part text
        row[7] = "MSG-129";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[11] = "ban-do.pdf";
        row[12] = 5120L;                      // có nội dung nhị phân
        row[14] = 2;                          // hai body part
        row[18] = 22;
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("file-transfer-body-part", gwout.getBodyPartType());
            assertEquals("ftbp", gwout.getBodyType());
            assertEquals("ban-do.pdf", gwout.getFtbpFileName());
            assertEquals("5120", gwout.getFtbpObjectSize());
            return true;
        }));
    }

    @Test
    void testSyncAmhsToGwout_NoAttachment_ShouldStayText() {
        // Không có data -> giữ nguyên loại body part do 188 báo
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[22];
        row[0] = 59300L;
        row[1] = "METAR VVTS 121200Z";
        row[2] = "121200";
        row[3] = "FF";
        row[5] = "401";
        row[7] = "MSG-130";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[12] = 0L;   // OCTET_LENGTH(data) = 0
        row[18] = 22;
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("ia5-text-body-part", gwout.getBodyPartType());
            assertEquals("text", gwout.getBodyType());
            assertEquals(null, gwout.getFtbpFileName());
            return true;
        }));
    }

    /**
     * CTSW003 - Bảng quyết định Delivery Report theo per-recipient-indicators.
     * DR chỉ sinh khi originator-report-request = report(2) HOẶC
     * originating-MTA-report-request = report(2)/audited-report(3).
     */
    private Gwout syncOneRowWithReportFlags(Integer reportRequest, Integer mtaReportRequest) {
        Object[] row = new Object[18];
        row[0] = 60001L;
        row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
        row[2] = "070430";
        row[3] = "FF";
        row[4] = null;
        row[5] = "401";
        row[6] = "IPM-CTSW003";
        row[7] = "MSG-CTSW003";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[10] = null;
        row[11] = null;
        row[12] = null;
        row[13] = null;
        row[14] = 1;              // numberOfAttachment
        row[15] = "ia5-text";     // originEncodeInformationType
        row[16] = reportRequest;      // mtcu_to.reportRequest
        row[17] = mtaReportRequest;   // mtcu_to.mtaReportRequest

        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        org.mockito.ArgumentCaptor<Gwout> captor = org.mockito.ArgumentCaptor.forClass(Gwout.class);
        verify(gwoutRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void testCTSW003_NoReport_AndMtaNonDeliveryReport_ShouldNotRequireDr() {
        // Bản tin 1: no-report(0) + non-delivery-report(1) -> không sinh DR
        assertEquals(Boolean.FALSE, syncOneRowWithReportFlags(0, 1).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_NoReport_AndMtaReport_ShouldRequireDr() {
        // Bản tin 2: no-report(0) + report(2) -> sinh DR
        assertEquals(Boolean.TRUE, syncOneRowWithReportFlags(0, 2).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_NoReport_AndMtaAuditedReport_ShouldRequireDr() {
        // Bản tin 3: no-report(0) + audited-report(3) -> sinh DR
        assertEquals(Boolean.TRUE, syncOneRowWithReportFlags(0, 3).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_NonDeliveryReport_AndMtaNonDeliveryReport_ShouldNotRequireDr() {
        // Bản tin 4: non-delivery-report(1) + non-delivery-report(1) -> không sinh DR
        assertEquals(Boolean.FALSE, syncOneRowWithReportFlags(1, 1).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_NonDeliveryReport_AndMtaReport_ShouldRequireDr() {
        // Bản tin 5: non-delivery-report(1) + report(2) -> sinh DR
        assertEquals(Boolean.TRUE, syncOneRowWithReportFlags(1, 2).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_NonDeliveryReport_AndMtaAuditedReport_ShouldRequireDr() {
        // Bản tin 6: non-delivery-report(1) + audited-report(3) -> sinh DR
        assertEquals(Boolean.TRUE, syncOneRowWithReportFlags(1, 3).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_OriginatorReportRequest_ShouldRequireDr() {
        // originator-report-request = report(2) là điều kiện độc lập theo Appendix A
        assertEquals(Boolean.TRUE, syncOneRowWithReportFlags(2, 1).getAmhsDeliveryReport());
    }

    @Test
    void testCTSW003_MissingFlags_ShouldDefaultToNoDr() {
        // Dòng cũ thiếu cột report-request -> không được suy diễn thành có DR
        assertEquals(Boolean.FALSE, syncOneRowWithReportFlags(null, null).getAmhsDeliveryReport());
    }

    /** CTSW008 - content-type của MTE phải được đưa xuống gwout để kiểm §4.4.1.1. */
    @Test
    void testCTSW008_ContentTypeIsCarriedIntoGwout() {
        Object[] row = new Object[19];
        row[0] = 60002L;
        row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
        row[2] = "070430";
        row[3] = "FF";
        row[5] = "401";
        row[7] = "MSG-CTSW008";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[18] = 35; // edi-messaging

        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        verify(gwoutRepository).saveAndFlush(argThat(gwout -> {
            assertEquals(Integer.valueOf(35), gwout.getX400ContentType());
            return true;
        }));
    }

    /** Dòng cũ không có cột contentType -> để NULL, không suy diễn thành giá trị hợp lệ. */
    @Test
    void testCTSW008_MissingContentTypeColumn_ShouldStayNull() {
        assertEquals(null, syncOneRowWithReportFlags(1, 1).getX400ContentType());
    }

    // ==================== CTSW001: subject ====================

    /**
     * Dựng một dòng mtcu_tmp/mtcu_to đầy đủ 20 cột với bodyPartType và charset tuỳ chọn.
     */
    private Gwout syncOneRow(String bodyPartType, String bodyPartCharacterSet, String subject) {
        Object[] row = new Object[20];
        row[0] = 60002L;
        row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
        row[2] = "070430";
        row[3] = "FF";
        row[4] = null;
        row[5] = bodyPartType;
        row[6] = "IPM-SUBJ";
        row[7] = "MSG-SUBJ";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[10] = bodyPartCharacterSet;
        row[11] = null;
        row[12] = null;
        row[13] = null;
        row[14] = 1;
        row[15] = "ia5-text";
        row[16] = 1;
        row[17] = 1;
        row[18] = 22;
        row[19] = subject;

        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        org.mockito.ArgumentCaptor<Gwout> captor = org.mockito.ArgumentCaptor.forClass(Gwout.class);
        verify(gwoutRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void testCTSW001_SubjectShouldBeCopiedFromMtcuTmp() {
        // CTSW001 (§4.4.3.4.8): subject của IPM heading phải được đồng bộ để publish amhs_subject
        assertEquals("SIGMET VVTS", syncOneRow("401", null, "SIGMET VVTS").getSubject());
    }

    @Test
    void testCTSW001_MissingSubject_ShouldStayNull() {
        assertEquals(null, syncOneRow("401", null, null).getSubject());
    }

    // ==================== CTSW017/CTSW018/CTSW019: repertoire ====================

    @Test
    void testRepertoire_RegistrationNumbers1And6_ShouldMapToIso646() {
        // CTSW018: character set registration numbers 1 và 6 = Basic ISO 646
        assertEquals("ISO-646", syncOneRow("402", "1,6", null).getBodyPartCharset());
    }

    @Test
    void testRepertoire_RegistrationNumbers1_6_100_ShouldMapToIso8859() {
        // CTSW019 điện văn 1: registration numbers 1, 6, 100 = ISO 8859-1
        assertEquals("ISO-8859-1", syncOneRow("402", "1,6,100", null).getBodyPartCharset());
    }

    @Test
    void testRepertoire_NonIso646RegistrationNumber_ShouldBePreserved() {
        // CTSW019 điện văn 2: Cyrillic (144) -> giữ lại để áp dụng chính sách nội bộ
        assertEquals("ISO-REG-144", syncOneRow("402", "1,6,144", null).getBodyPartCharset());
    }

    @Test
    void testRepertoire_TextualIso8859_ShouldStillMap() {
        assertEquals("ISO-8859-1", syncOneRow("402", "ISO 8859-1", null).getBodyPartCharset());
    }

    @Test
    void testRepertoire_Ia5TextBodyPart_ShouldCarryIta2ForCtsw017() {
        // CTSW017 điện văn 3: repertoire ita2 phải được giữ lại trên ia5-text-body-part
        // để OutboundDispatchService từ chối bản tin
        assertEquals("ITA2", syncOneRow("401", "ITA2", null).getBodyPartCharset());
    }

    @Test
    void testRepertoire_FileTransferBodyPart_ShouldNotCarryCharset() {
        // FTBP không có tham số repertoire
        assertEquals(null, syncOneRow("403", "1,6", null).getBodyPartCharset());
    }

    // ============ CTSW001 / CTSW020: precedence và responsibility theo recipient ============

    /**
     * Dựng một IPM có nhiều recipient, mỗi recipient một cặp (precedence, responsibility).
     * Mảng row có 22 cột: [20] = precedence, [21] = responsibility.
     */
    private Gwout syncWithRecipients(String atsPriority, Object[][] recipients) {
        List<Object[]> mockRows = new ArrayList<>();
        for (int i = 0; i < recipients.length; i++) {
            Object[] row = new Object[22];
            row[0] = 60004L;
            row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
            row[2] = "070430";
            row[3] = atsPriority;
            row[5] = "401";
            row[6] = "IPM-PREC";
            row[7] = "MSG-PREC";
            row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
            row[9] = String.format("/CN=%s/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/", recipients[i][0]);
            row[14] = 1;
            row[15] = "ia5-text";
            row[16] = 1;
            row[17] = 1;
            row[18] = 22;
            row[20] = recipients[i][1]; // precedence
            row[21] = recipients[i][2]; // responsibility
            mockRows.add(row);
        }

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        org.mockito.ArgumentCaptor<Gwout> captor = org.mockito.ArgumentCaptor.forClass(Gwout.class);
        verify(gwoutRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void testCTSW001_HighestPrecedenceAmongRecipients_DrivesPriority() {
        // CTSW001 điện văn 6: hai recipient, precedence 107 và 14 -> lấy CAO NHẤT là 107
        Gwout gwout = syncWithRecipients(null, new Object[][] {
                { "VVHHZTZX", 107, true },
                { "VVNBZTZX", 14, true }
        });

        assertEquals(Integer.valueOf(107), gwout.getPrecedence());
        assertEquals("SS", gwout.getAmhsPriority());   // Table 5: 107 -> SS
        assertEquals(Integer.valueOf(6), gwout.getSwimPriority()); // Table 3: SS -> 6
    }

    @Test
    void testCTSW001_PrecedenceTable5Mapping() {
        assertEquals("KK", syncWithRecipients(null, new Object[][] { { "VVHHZTZX", 14, true } }).getAmhsPriority());
    }

    @Test
    void testCTSW001_OnlyResponsibleRecipientsCountForPrecedence() {
        // Recipient "not responsible" có precedence cao hơn nhưng không được tính
        Gwout gwout = syncWithRecipients(null, new Object[][] {
                { "VVHHZTZX", 28, true },
                { "VVNBZTZX", 107, false }
        });

        assertEquals(Integer.valueOf(28), gwout.getPrecedence());
        assertEquals("GG", gwout.getAmhsPriority());
    }

    @Test
    void testCTSW001_AmhsRecipientsExcludesNotResponsible() {
        // §4.4.3.4.4: amhs_recipients chỉ gồm recipient có responsibility = responsible
        Gwout gwout = syncWithRecipients(null, new Object[][] {
                { "VVHHZTZX", 57, true },
                { "VVNBZTZX", 57, false },
                { "VVDNZTZX", 57, true }
        });

        assertEquals("VVHHZTZX,VVDNZTZX", gwout.getAddress());
    }

    @Test
    void testBasicIpm_WithoutPrecedence_KeepsAtsMessagePriority() {
        // Basic IPM: không có precedence -> giữ ATS-message-priority như cũ
        Gwout gwout = syncWithRecipients("FF", new Object[][] {
                { "VVHHZTZX", null, null }
        });

        assertEquals(null, gwout.getPrecedence());
        assertEquals("FF", gwout.getAmhsPriority());
        assertEquals(Integer.valueOf(4), gwout.getSwimPriority());
    }

    @Test
    void testResponsibilityAllNull_ShouldKeepEveryRecipient() {
        // amss chưa cung cấp dữ liệu -> không được âm thầm loại bỏ recipient nào
        Gwout gwout = syncWithRecipients("FF", new Object[][] {
                { "VVHHZTZX", null, null },
                { "VVNBZTZX", null, null }
        });

        assertEquals("VVHHZTZX,VVNBZTZX", gwout.getAddress());
    }

    @Test
    void testUnknownPrecedence_ShouldFallBackToAtsPriority() {
        // Precedence ngoài Table 5 -> không suy diễn sai, giữ ATS-message-priority
        Gwout gwout = syncWithRecipients("DD", new Object[][] {
                { "VVHHZTZX", 99, true }
        });

        assertEquals(Integer.valueOf(99), gwout.getPrecedence());
        assertEquals("DD", gwout.getAmhsPriority());
    }

    // ==================== CTSW010: sức chứa danh sách recipient ====================

    @Test
    void testCTSW010_512Recipients_ShouldNotBeTruncated() {
        // CTSW010 ca (a): 512 recipient phải được giữ nguyên vẹn để publish thành công.
        // Trước đây gwout.address bị cắt ở 1000 ký tự -> mất recipient âm thầm.
        int recipientCount = 512;
        List<Object[]> mockRows = new ArrayList<>();
        for (int i = 0; i < recipientCount; i++) {
            Object[] row = new Object[20];
            row[0] = 60003L; // cùng một IPM
            row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
            row[2] = "070430";
            row[3] = "FF";
            row[5] = "401";
            row[6] = "IPM-CTSW010";
            row[7] = "MSG-CTSW010";
            row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
            // 512 địa chỉ AFTN 8 ký tự khác nhau: VVxxxxZX với xxxx là số thứ tự
            row[9] = String.format("/CN=VV%04dZX/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/", i);
            row[14] = 1;
            row[15] = "ia5-text";
            row[16] = 1;
            row[17] = 1;
            row[18] = 22;
            mockRows.add(row);
        }

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        org.mockito.ArgumentCaptor<Gwout> captor = org.mockito.ArgumentCaptor.forClass(Gwout.class);
        verify(gwoutRepository).saveAndFlush(captor.capture());
        String address = captor.getValue().getAddress();

        assertEquals(recipientCount, address.split(",").length,
                "Phải giữ đủ 512 recipient, không được cắt bớt");
        assertTrue(address.length() > 1000,
                "Chuỗi 512 recipient dài hơn giới hạn varchar(1000) cũ (~4.6KB)");
    }

    // ==================== CTSW004: filing-time sai khuôn ====================

    @Test
    void testCTSW004_MalformedFilingTime_ShouldNotBeTruncatedIntoAValidValue() {
        // Cắt về 6 ký tự lúc đồng bộ sẽ biến "0704301234" thành "070430" hợp lệ, và
        // validateAtsMessageHeader (chạy sau, ở OutboundDispatchService) mất luôn ca kiểm thử này.
        // Cột gwout.filing_time đã nới lên varchar(32) để giữ nguyên giá trị hỏng.
        Gwout gwout = syncOneRowWithFilingTime("0704301234");

        assertEquals("0704301234", gwout.getFilingTime());
    }

    @Test
    void testValidFilingTime_ShouldBeUnchanged() {
        assertEquals("070430", syncOneRowWithFilingTime("070430").getFilingTime());
    }

    private Gwout syncOneRowWithFilingTime(String atsFilingTime) {
        Object[] row = new Object[20];
        row[0] = 60003L;
        row[1] = "METAR VVNB 070430Z 15004KT 9999 FEW020 28/24 Q1010 NOSIG=";
        row[2] = atsFilingTime;
        row[3] = "FF";
        row[4] = null;
        row[5] = "401";
        row[6] = "IPM-FT";
        row[7] = "MSG-FT";
        row[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        row[14] = 1;
        row[15] = "ia5-text";
        row[16] = 1;
        row[17] = 1;
        row[18] = 22;

        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(row);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        scheduler.syncAmhsToGwout();

        org.mockito.ArgumentCaptor<Gwout> captor = org.mockito.ArgumentCaptor.forClass(Gwout.class);
        verify(gwoutRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
