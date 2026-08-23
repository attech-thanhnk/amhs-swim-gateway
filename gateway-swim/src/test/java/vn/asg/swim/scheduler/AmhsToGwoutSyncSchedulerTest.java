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
import vn.asg.swim.service.ConfigService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ConfigService configService;

    private AmhsToGwoutSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AmhsToGwoutSyncScheduler(entityManager, gwoutRepository, configService);
    }

    @Test
    void testSyncAmhsToGwout_NoNewMessages() {
        // Given: Empty result list
        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
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

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
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
    void testSyncAmhsToGwout_MultipleRecipients_ShouldJoinRealRecipientsExcludingGateway() {
        // Given: 1 IPM (mtcu_tmp.id=59296) có 3 dòng mtcu_to: gateway (VVTSSWIM) + 2 recipient thật
        List<Object[]> mockRows = new ArrayList<>();

        Object[] rowGateway = new Object[14];
        rowGateway[0] = 59296L;
        rowGateway[1] = "METAR VVCI 070130Z 21005KT 150V250 9999 BKN019 31/26 Q1002 NOSIG=";
        rowGateway[2] = "070130";
        rowGateway[3] = "FF";
        rowGateway[4] = null;
        rowGateway[5] = "401";
        rowGateway[6] = "IPM-126";
        rowGateway[7] = "MSG-126";
        rowGateway[8] = "/CN=VVCIYMYX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        rowGateway[9] = "/CN=VVTSSWIM/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        rowGateway[10] = "ISO-8859-1";
        mockRows.add(rowGateway);

        Object[] rowRecipient1 = rowGateway.clone();
        rowRecipient1[9] = "/CN=VVCIZTZX/OU=VVCI/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        mockRows.add(rowRecipient1);

        Object[] rowRecipient2 = rowGateway.clone();
        rowRecipient2[9] = "/CN=VVHHZTZX/OU=VVHH/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/";
        mockRows.add(rowRecipient2);

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then: chỉ 1 gwout được tạo (gộp theo mtcu_tmp.id), amhs_recipients gồm 2 recipient thật, không có VVTSSWIM
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("MSG-126", gwout.getAmhsid());
            assertEquals("VVCIZTZX,VVHHZTZX", gwout.getAddress());
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

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
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
        row[11] = "report.pdf"; // ftbpFileName
        row[12] = "204800"; // ftbpObjectSize
        row[13] = "20260816120000Z"; // ftbpLastMod
        mockRows.add(row);

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then
        verify(gwoutRepository, times(1)).saveAndFlush(argThat(gwout -> {
            assertEquals("file-transfer-body-part", gwout.getBodyPartType());
            assertEquals("ftbp", gwout.getBodyType());
            assertEquals("report.pdf", gwout.getFtbpFileName());
            assertEquals("204800", gwout.getFtbpObjectSize());
            assertEquals("20260816120000Z", gwout.getFtbpLastMod());
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

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
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

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
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
}
