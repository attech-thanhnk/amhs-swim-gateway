package vn.asg.swim.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutDispatch;
import vn.asg.swim.repository.GwoutDispatchRepository;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.service.AlertService;
import vn.asg.swim.service.ConfigService;
import vn.asg.swim.service.ConnectionManagerService;
import vn.asg.swim.service.OutboundDispatchService;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for GwoutPollerScheduler.
 * Covers critical bug fixes: Issue #6 (AFTN validation), Issue #7 (alert on no recipients).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GwoutPollerSchedulerTest {

    @Mock private GwoutRepository gwoutRepository;
    @Mock private GwoutDispatchRepository gwoutDispatchRepository;
    @Mock private OutboundDispatchService outboundDispatchService;
    @Mock private ConnectionManagerService connectionManager;
    @Mock private ConfigService configService;
    @Mock private AlertService alertService;

    @InjectMocks
    private GwoutPollerScheduler scheduler;

    private Gwout gwout;
    private AtomicBoolean connected;

    @BeforeEach
    void setUp() {
        gwout = new Gwout();
        gwout.setMsgid(1L);
        gwout.setText("METAR VVTS 121200Z 09008KT 9999 FEW020 32/25 Q1010=");
        gwout.setStatus(Gwout.STATUS_PENDING);

        connected = new AtomicBoolean(true);
        when(connectionManager.getConnected()).thenReturn(connected);
        when(configService.getInt("OUTBOUND_BATCH_SIZE")).thenReturn(10);
        when(configService.getPollIntervalMs()).thenReturn(5000L);
    }

    // ==================== POLL GWOUT TASK ====================

    @Test
    void testPollGwout_NotConnected_ShouldSkip() {
        // Given: AMQP not connected
        connected.set(false);

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should not query database
        verify(gwoutRepository, never()).findPendingBatch(anyInt());
    }

    @Test
    void testPollGwout_EmptyBatch_ShouldDoNothing() {
        // Given: No pending gwout records
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of());

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should not create any dispatches
        verify(gwoutDispatchRepository, never()).save(any());
    }

    // ==================== ISSUE #7: NO RECIPIENTS ALERT ====================

    @Test
    void testNoRecipients_ShouldSetDead_AndCreateAlert() {
        // Given: gwout with no recipients
        gwout.setAddress(null);
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should set DEAD status
        verify(gwoutRepository).save(argThat(g ->
            g.getStatus().equals(Gwout.STATUS_DEAD)
        ));

        // Verify alert created (Issue #7 fix)
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("has no recipients"),
            eq("gwout"),
            eq(1L)
        );
    }

    @Test
    void testBlankRecipients_ShouldSetDead_AndCreateAlert() {
        // Given: gwout with blank recipients
        gwout.setAddress("   ");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should set DEAD and alert
        verify(gwoutRepository).save(argThat(g ->
            g.getStatus().equals(Gwout.STATUS_DEAD)
        ));
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            anyString(),
            eq("gwout"),
            eq(1L)
        );
    }

    // ==================== ISSUE #6: AFTN ADDRESS VALIDATION ====================

    @Test
    void testAftnValidation_ValidAddresses_ShouldCreateDispatches() {
        // Given: Valid AFTN addresses (8 uppercase letters)
        gwout.setAddress("VVHHZTZX VVTSZDYX VVNBZYYX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should create 3 dispatches
        verify(gwoutDispatchRepository, times(3)).save(any(GwoutDispatch.class));
        verify(gwoutRepository).save(argThat(g ->
            g.getStatus().equals(Gwout.STATUS_PROCESSING)
        ));
    }

    @Test
    void testAftnValidation_InvalidFormat_ShouldFilterOut() {
        // Given: Mixed valid and invalid addresses
        gwout.setAddress("VVHHZTZX invalid123 VVTS TOOLONGADDRESS VVNBZYYX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should only create dispatches for 2 valid addresses
        verify(gwoutDispatchRepository, times(2)).save(argThat(d ->
            d.getRecipient().equals("VVHHZTZX") || d.getRecipient().equals("VVNBZYYX")
        ));
    }

    @Test
    void testAftnValidation_AllInvalid_ShouldSetDead() {
        // Given: All addresses invalid
        gwout.setAddress("invalid123 abc TOOLONG");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should set DEAD with alert
        verify(gwoutRepository).save(argThat(g ->
            g.getStatus().equals(Gwout.STATUS_DEAD)
        ));
        verify(alertService).create(
            eq(GwAlert.TYPE_VALIDATION_ERROR),
            eq(GwAlert.SEV_WARNING),
            contains("no valid AFTN recipients"),
            eq("gwout"),
            eq(1L)
        );
        verify(gwoutDispatchRepository, never()).save(any());
    }

    @Test
    void testAftnValidation_LowercaseAddress_ShouldBeFiltered() {
        // Given: Lowercase addresses (invalid)
        gwout.setAddress("vvhhztzx VVTSZDYX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Only uppercase should pass
        verify(gwoutDispatchRepository, times(1)).save(argThat(d ->
            d.getRecipient().equals("VVTSZDYX")
        ));
    }

    @Test
    void testAftnValidation_WithCommaDelimiter_ShouldSplit() {
        // Given: Comma-separated addresses
        gwout.setAddress("VVHHZTZX,VVTSZDYX, VVNBZYYX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should split correctly
        verify(gwoutDispatchRepository, times(3)).save(any(GwoutDispatch.class));
    }

    // ==================== DEDUPLICATION ====================

    @Test
    void testDuplicateRecipients_ShouldBeDistinct() {
        // Given: Duplicate recipients
        gwout.setAddress("VVHHZTZX VVTSZDYX VVHHZTZX VVTSZDYX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should create only 2 unique dispatches
        verify(gwoutDispatchRepository, times(2)).save(any(GwoutDispatch.class));
    }

    // ==================== DISPATCH PROPERTIES ====================

    @Test
    void testCreateDispatches_ShouldSetCorrectProperties() {
        // Given: Valid gwout
        gwout.setAddress("VVHHZTZX");
        when(gwoutRepository.findPendingBatch(10)).thenReturn(List.of(gwout));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Dispatch should have correct properties
        verify(gwoutDispatchRepository).save(argThat(dispatch -> {
            assertEquals(1L, dispatch.getGwoutId());
            assertEquals("VVHHZTZX", dispatch.getRecipient());
            assertEquals(GwoutDispatch.STATUS_PENDING, dispatch.getStatus());
            return true;
        }));

        // Gwout status should be updated to PROCESSING
        verify(gwoutRepository).save(argThat(g ->
            g.getStatus().equals(Gwout.STATUS_PROCESSING)
        ));
    }

    // ==================== MULTIPLE GWOUT RECORDS ====================

    @Test
    void testMultipleGwout_ShouldProcessAll() {
        // Given: Multiple gwout records
        Gwout gwout1 = new Gwout();
        gwout1.setMsgid(1L);
        gwout1.setAddress("VVHHZTZX");
        gwout1.setStatus(Gwout.STATUS_PENDING);

        Gwout gwout2 = new Gwout();
        gwout2.setMsgid(2L);
        gwout2.setAddress("VVTSZDYX VVNBZYYX");
        gwout2.setStatus(Gwout.STATUS_PENDING);

        when(gwoutRepository.findPendingBatch(10)).thenReturn(Arrays.asList(gwout1, gwout2));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should create 3 total dispatches (1 + 2)
        verify(gwoutDispatchRepository, times(3)).save(any(GwoutDispatch.class));
        verify(gwoutRepository, times(2)).save(any(Gwout.class));
    }

    // ==================== ERROR HANDLING ====================

    @Test
    void testDatabaseError_ShouldContinueProcessing() {
        // Given: First gwout causes error, second is valid
        Gwout gwout1 = new Gwout();
        gwout1.setMsgid(1L);
        gwout1.setAddress("VVHHZTZX");

        Gwout gwout2 = new Gwout();
        gwout2.setMsgid(2L);
        gwout2.setAddress("VVTSZDYX");

        when(gwoutRepository.findPendingBatch(10)).thenReturn(Arrays.asList(gwout1, gwout2));
        doThrow(new RuntimeException("Database error"))
            .when(gwoutDispatchRepository).save(argThat(d -> d.getRecipient().equals("VVHHZTZX")));

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should still process second gwout
        verify(gwoutDispatchRepository, atLeastOnce()).save(argThat(d ->
            d.getRecipient().equals("VVTSZDYX")
        ));
    }

    // ==================== POLL DISPATCHES TASK ====================

    @Test
    void testPollDispatches_NotConnected_ShouldSkip() {
        // Given: Not connected
        connected.set(false);

        // When
        scheduler.pollDispatchesAndProcess();

        // Then: Should not process
        verify(gwoutDispatchRepository, never()).findPendingBatch(anyInt());
    }

    @Test
    void testPollDispatches_ShouldProcessEach() {
        // Given: Pending dispatches
        GwoutDispatch dispatch1 = new GwoutDispatch();
        dispatch1.setId(1L);
        dispatch1.setStatus(GwoutDispatch.STATUS_PENDING);

        GwoutDispatch dispatch2 = new GwoutDispatch();
        dispatch2.setId(2L);
        dispatch2.setStatus(GwoutDispatch.STATUS_PENDING);

        when(gwoutDispatchRepository.findPendingBatch(10))
            .thenReturn(Arrays.asList(dispatch1, dispatch2));

        // When
        scheduler.pollDispatchesAndProcess();

        // Then: Should call OutboundDispatchService for each
        verify(outboundDispatchService).processDispatch(dispatch1);
        verify(outboundDispatchService).processDispatch(dispatch2);
    }

    @Test
    void testPollDispatches_ProcessError_ShouldContinue() {
        // Given: First dispatch throws error
        GwoutDispatch dispatch1 = new GwoutDispatch();
        dispatch1.setId(1L);

        GwoutDispatch dispatch2 = new GwoutDispatch();
        dispatch2.setId(2L);

        when(gwoutDispatchRepository.findPendingBatch(10))
            .thenReturn(Arrays.asList(dispatch1, dispatch2));
        doThrow(new RuntimeException("Process error"))
            .when(outboundDispatchService).processDispatch(dispatch1);

        // When
        scheduler.pollDispatchesAndProcess();

        // Then: Should still process second dispatch
        verify(outboundDispatchService).processDispatch(dispatch2);
    }

    // ==================== BATCH SIZE CONFIG ====================

    @Test
    void testBatchSize_ShouldRespectConfig() {
        // Given: Batch size configured to 5
        when(configService.getInt("OUTBOUND_BATCH_SIZE")).thenReturn(5);

        // When
        scheduler.pollGwoutAndCreateDispatches();

        // Then: Should query with batch size 5
        verify(gwoutRepository).findPendingBatch(5);
    }
}
