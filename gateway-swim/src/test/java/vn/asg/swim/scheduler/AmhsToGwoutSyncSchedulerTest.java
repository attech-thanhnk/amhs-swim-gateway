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
        verify(gwoutRepository, never()).save(any(Gwout.class));
    }

    @Test
    void testSyncAmhsToGwout_WithNewMessages() {
        // Given: A mock row from mtcu_tmp/mtcu_to
        List<Object[]> mockRows = new ArrayList<>();
        Object[] row = new Object[10];
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
        mockRows.add(row);

        when(configService.getDefaultOriginator()).thenReturn("VVTSSWIM");
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(mockRows);

        // When
        scheduler.syncAmhsToGwout();

        // Then: Verify saved gwout details
        verify(gwoutRepository, times(1)).save(argThat(gwout -> {
            assertEquals("MSG-123", gwout.getAmhsid());
            assertEquals("VVCIYMYX", gwout.getOrigin());
            assertEquals("VVTSSWIM", gwout.getAddress());
            assertEquals("070130", gwout.getFilingTime());
            assertEquals("OHI-123", gwout.getOptionalHeading());
            assertEquals("FF", gwout.getAmhsPriority());
            assertEquals(6, gwout.getSwimPriority()); // FF maps to SWIM priority 6
            assertEquals(MessageStatus.OUT_PENDING.getValue(), gwout.getStatus());
            assertEquals("text", gwout.getBodyType());
            return true;
        }));
    }
}
