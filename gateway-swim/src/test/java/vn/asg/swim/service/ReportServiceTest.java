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
import vn.asg.swim.entity.Gwout;
import vn.asg.swim.entity.GwoutReport;
import vn.asg.swim.repository.GwoutReportRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EUR Doc 047 §4.4.8 - hàng đợi AMHS report gửi AMHS Component.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private GwoutReportRepository reportRepository;

    @InjectMocks
    private ReportService service;

    private Gwout gwout;

    @BeforeEach
    void setUp() {
        gwout = new Gwout();
        gwout.setMsgid(42L);
        gwout.setAmhsid("MTS-ID-42");
        gwout.setAddress("VVHHZTZX,VVNBZTZX");

        when(reportRepository.findByGwoutIdAndRecipientAndReportType(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    private List<GwoutReport> captureSaved(int times) {
        ArgumentCaptor<GwoutReport> captor = ArgumentCaptor.forClass(GwoutReport.class);
        verify(reportRepository, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void testNdrForAll_ShouldCreateOneRowPerRecipient() {
        // Report X.400 mang per-recipient-fields -> mỗi recipient một dòng
        service.recordNdrForAll(gwout, "content-too-long",
                "unable to convert to AMQP due to the content size");

        List<GwoutReport> saved = captureSaved(2);
        assertEquals(List.of("VVHHZTZX", "VVNBZTZX"),
                saved.stream().map(GwoutReport::getRecipient).toList());
        for (GwoutReport r : saved) {
            assertEquals(GwoutReport.TYPE_NDR, r.getReportType());
            assertEquals(GwoutReport.REASON_UNABLE_TO_TRANSFER, r.getReasonCode());
            assertEquals("content-too-long", r.getDiagnosticCode());
            assertEquals("unable to convert to AMQP due to the content size", r.getSupplementaryInfo());
            assertEquals(GwoutReport.STATUS_PENDING, r.getStatus());
            assertEquals(42L, r.getGwoutId());
            assertEquals("MTS-ID-42", r.getMtsId());
        }
    }

    @Test
    void testNdrForAll_WithoutSupplementary_ShouldLeaveItNull() {
        // CTSW005/CTSW008/CTSW016 chỉ yêu cầu reason-code + diagnostic-code
        service.recordNdrForAll(gwout, "maximum-time-expired", null);

        for (GwoutReport r : captureSaved(2)) {
            assertNull(r.getSupplementaryInfo());
        }
    }

    @Test
    void testDrForAll_ShouldNotCarryNdrFields() {
        // CTSW003: DR không có reason/diagnostic/supplementary
        service.recordDrForAll(gwout);

        for (GwoutReport r : captureSaved(2)) {
            assertEquals(GwoutReport.TYPE_DR, r.getReportType());
            assertNull(r.getReasonCode());
            assertNull(r.getDiagnosticCode());
            assertNull(r.getSupplementaryInfo());
        }
    }

    @Test
    void testCombinedReport_MixedDrAndNdrForSameMessage() {
        // CTSW012: cùng một gwout_id vừa có DR vừa có NDR -> amss gom thành combined report
        service.recordDr(gwout, "VVHHZTZX");
        service.recordNdr(gwout, "VVZZZTZX", "unrecognised-OR-name", null);

        List<GwoutReport> saved = captureSaved(2);
        assertEquals(GwoutReport.TYPE_DR, saved.get(0).getReportType());
        assertEquals("VVHHZTZX", saved.get(0).getRecipient());
        assertEquals(GwoutReport.TYPE_NDR, saved.get(1).getReportType());
        assertEquals("VVZZZTZX", saved.get(1).getRecipient());
        assertEquals("unrecognised-OR-name", saved.get(1).getDiagnosticCode());
        // Cùng bản tin gốc -> amss gom được theo gwout_id
        assertEquals(saved.get(0).getGwoutId(), saved.get(1).getGwoutId());
    }

    @Test
    void testIdempotency_ExistingRowShouldNotBeDuplicated() {
        when(reportRepository.findByGwoutIdAndRecipientAndReportType(42L, "VVHHZTZX", GwoutReport.TYPE_NDR))
                .thenReturn(Optional.of(new GwoutReport()));

        service.recordNdrForAll(gwout, "content-syntax-error", null);

        // Chỉ recipient thứ hai được ghi
        List<GwoutReport> saved = captureSaved(1);
        assertEquals("VVNBZTZX", saved.get(0).getRecipient());
    }

    @Test
    void testRepositoryFailure_ShouldNotPropagate() {
        // Lỗi ghi report không được làm hỏng luồng xử lý bản tin chính
        when(reportRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> service.recordNdrForAll(gwout, "content-syntax-error", null));
    }

    @Test
    void testBlankAddress_ShouldStillRecordOneRow() {
        // Bản tin không có recipient hợp lệ vẫn phải để lại dấu vết cho amss
        gwout.setAddress(null);

        service.recordNdrForAll(gwout, "unrecognised-OR-name", null);

        assertEquals("UNKNOWN", captureSaved(1).get(0).getRecipient());
    }

    @Test
    void testSupplementaryLongerThanColumn_ShouldBeTruncated() {
        service.recordNdr(gwout, "VVHHZTZX", "content-syntax-error", "x".repeat(600));

        assertEquals(512, captureSaved(1).get(0).getSupplementaryInfo().length());
    }
}
