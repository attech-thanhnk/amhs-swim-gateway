package vn.asg.swim.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.McuIpn;
import vn.asg.swim.repository.GwoutRepository;
import vn.asg.swim.repository.McuIpnRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EUR Doc 047 §4.4.7 - xử lý IPN đến từ AMHS (Appendix A CTSW014, CTSW015).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpnProcessingServiceTest {

    @Mock private McuIpnRepository ipnRepository;
    @Mock private GwoutRepository gwoutRepository;
    @Mock private ReportService reportService;
    @Mock private AlertService alertService;
    @Mock private MessageConversionService conversionService;

    @InjectMocks
    private IpnProcessingService service;

    private McuIpn ipn;

    @BeforeEach
    void setUp() {
        ipn = new McuIpn();
        ipn.setId(7L);
        ipn.setNotificationType(McuIpn.TYPE_RN);
        ipn.setSubjectIpmId("IPM-SS-001");
        ipn.setOrAddress("VVHHZTZX");

        when(gwoutRepository.findByIpmId(anyString())).thenReturn(List.of());
        when(gwoutRepository.findByAmhsid(anyString())).thenReturn(List.of());
    }

    private Gwout subjectWithPriority(String priority) {
        Gwout subject = new Gwout();
        subject.setMsgid(500L);
        subject.setIpmId("IPM-SS-001");
        subject.setAmhsPriority(priority);
        return subject;
    }

    @Test
    void testCTSW014_SubjectWithSsPriority_ShouldBeAcceptedAndReported() {
        // CTSW014 điện văn 1: RN có bản tin chủ đề priority SS -> không bị từ chối,
        // được log và báo Control Position (§4.4.7.3)
        when(gwoutRepository.findByIpmId("IPM-SS-001")).thenReturn(List.of(subjectWithPriority("SS")));

        service.processIpn(ipn);

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), contains("đã được nhận"),
                eq("mtcu_ipn"), eq(7L));
        verify(conversionService).logAmhsToSwim(any(Gwout.class), isNull(), eq("OK"), contains("ipn_accepted"));
        // RN hợp lệ không sinh NDR
        verify(reportService, never()).recordNdrForAll(any(), anyString(), any());
        assertEquals(McuIpn.STATUS_PROCESSED, ipn.getStatus());
    }

    @Test
    void testCTSW014_SubjectWithNonSsPriority_ShouldBeRejectedWithoutNdr() {
        // CTSW014 điện văn 2: priority DD khác SS -> từ chối, log + báo Control Position,
        // KHÔNG sinh NDR (§4.4.7.2)
        when(gwoutRepository.findByIpmId("IPM-SS-001")).thenReturn(List.of(subjectWithPriority("DD")));

        service.processIpn(ipn);

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("khác SS"),
                eq("mtcu_ipn"), eq(7L));
        verify(conversionService).logAmhsToSwim(any(Gwout.class), isNull(), eq("REJECTED"),
                contains("ipn_rejected_priority"));
        verify(reportService, never()).recordNdrForAll(any(), anyString(), any());
        assertEquals(McuIpn.STATUS_PROCESSED, ipn.getStatus());
    }

    @Test
    void testCTSW015_NoSubjectMessage_ShouldGenerateMisroutedNdr() {
        // CTSW015: RN có bản tin chủ đề hư cấu -> NDR invalid-arguments kèm supplementary
        ipn.setSubjectIpmId("IPM-KHONG-TON-TAI");

        service.processIpn(ipn);

        ArgumentCaptor<Gwout> captor = ArgumentCaptor.forClass(Gwout.class);
        verify(reportService).recordNdrForAll(captor.capture(), eq("invalid-arguments"),
                eq("unable to notify RN to SWIM due to misrouted RN"));
        // NDR gửi về chính bên đã phát RN
        assertEquals("VVHHZTZX", captor.getValue().getAddress());

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("lạc tuyến"),
                eq("mtcu_ipn"), eq(7L));
        verify(conversionService).logAmhsToSwimRejected(any(Gwout.class), contains("ndr_misrouted_ipn"),
                eq("invalid-arguments"), eq("unable to notify RN to SWIM due to misrouted RN"));
        assertEquals(McuIpn.STATUS_PROCESSED, ipn.getStatus());
    }

    @Test
    void testFallbackToMtsIdentifier_WhenIpmIdMissing() {
        ipn.setSubjectIpmId(null);
        ipn.setSubjectMtsId("MTS-SS-001");
        when(gwoutRepository.findByAmhsid("MTS-SS-001")).thenReturn(List.of(subjectWithPriority("SS")));

        service.processIpn(ipn);

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), anyString(), anyString(), anyLong());
        verify(reportService, never()).recordNdrForAll(any(), anyString(), any());
    }

    @Test
    void testProcessedFlag_ShouldAlwaysBePersisted() {
        // Dù đi nhánh nào, bản ghi cũng phải được đánh dấu để không xử lý lặp
        service.processIpn(ipn);

        verify(ipnRepository).save(ipn);
        assertEquals(McuIpn.STATUS_PROCESSED, ipn.getStatus());
    }
}
