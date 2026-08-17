package vn.asg.swim.scheduler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.MessageStatus;
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
            assertEquals(MessageStatus.OUT_PENDING.getValue(), gwout.getStatus());
            assertEquals("text", gwout.getBodyType());
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
}
