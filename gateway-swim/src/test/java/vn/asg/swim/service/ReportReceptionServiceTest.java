package vn.asg.swim.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.entity.Gwin;
import vn.asg.swim.entity.InboundStatus;
import vn.asg.swim.entity.McuReport;
import vn.asg.swim.repository.GwinRepository;
import vn.asg.swim.repository.McuReportRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EUR Doc 047 §4.4.1.3 (Report reception) - Appendix A CTSW114.
 * <p>
 * Kịch bản: bản tin AMQP đi qua gateway ra AMHS, bị AMHS Test Tool xếp hàng rồi xoá, Test Tool
 * trả NDR về. Tiêu chí chấm gọn đúng một câu - <i>"Check that the IUT logs and reports the
 * situation to the Control Position."</i>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportReceptionServiceTest {

    @Mock private McuReportRepository reportRepository;
    @Mock private GwinRepository gwinRepository;
    @Mock private AlertService alertService;
    @Mock private MessageConversionService conversionService;

    @InjectMocks
    private ReportReceptionService service;

    private Gwin subject;

    @BeforeEach
    void setUp() {
        subject = new Gwin();
        subject.setMsgid(42L);
        subject.setMessageId("amqp-msg-114");
        subject.setMtsId("MTS-114");
        subject.setIpmId("IPM-114");
        subject.setOrigin("VVTSSWIM");
        subject.setStatus(10);

        when(gwinRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of());
    }

    /** NDR đúng khuôn CTSW114: reason unable-to-transfer, diagnostic ĐỂ TRỐNG */
    private McuReport ctsw114Ndr() {
        McuReport report = new McuReport();
        report.setId(1L);
        report.setReportType(McuReport.TYPE_NDR);
        report.setSubjectMtsId("MTS-114");
        report.setRecipient("VVHHZTZX");
        report.setReasonCode("unable-to-transfer");
        report.setDiagnosticCode(null);
        return report;
    }

    @Test
    @DisplayName("CTSW114: NDR về -> ghi log và báo Control Position")
    void testCTSW114_NdrShouldBeLoggedAndReportedToControlPosition() {
        when(gwinRepository.findByMtsId("MTS-114")).thenReturn(List.of(subject));
        McuReport report = ctsw114Ndr();

        service.processReport(report);

        // "reports to the Control Position" = alert trên gw_alert, tham chiếu tới bản tin gốc
        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_ERROR),
                contains("không giao được"), eq("gwin"), eq(42L));
        // "logs" = traffic log
        verify(conversionService).logSwimToAmhs(eq("amqp-msg-114"), eq("VVTSSWIM"), eq("REJECTED"),
                contains("ndr_received"), eq("non-delivery-report"), eq("IPM-114"));
        assertEquals(McuReport.STATUS_PROCESSED, report.getStatus());
    }

    @Test
    @DisplayName("CTSW114: diagnostic-code ĐỂ TRỐNG là dữ liệu hợp lệ, không được loại bản ghi")
    void testCTSW114_EmptyDiagnosticCodeIsValid() {
        when(gwinRepository.findByMtsId("MTS-114")).thenReturn(List.of(subject));

        for (String emptyish : new String[] { null, "", "   " }) {
            reset(alertService, conversionService);
            McuReport report = ctsw114Ndr();
            report.setDiagnosticCode(emptyish);

            service.processReport(report);

            // Thiếu diagnostic thì lùi về reason-code, không bao giờ đẩy chuỗi rỗng lên giao diện
            verify(alertService).create(anyString(), anyString(), contains("unable-to-transfer"),
                    anyString(), anyLong());
            assertEquals(McuReport.STATUS_PROCESSED, report.getStatus());
        }
    }

    @Test
    @DisplayName("CTSW114: NDR phải sửa lại trạng thái bản tin gốc, không để hiển thị 'đã giao xong'")
    void testCTSW114_SubjectStatusMustBeCorrected() {
        when(gwinRepository.findByMtsId("MTS-114")).thenReturn(List.of(subject));

        service.processReport(ctsw114Ndr());

        assertEquals(InboundStatus.FAILED.getValue(), subject.getStatus());
        assertEquals("non-delivery-report", subject.getRejectionReason());
        assertEquals("unable-to-transfer", subject.getRejectionDiagnostic());
        // Lỗi đến từ phía AMHS. Setter của rejectionReason tự gán "SWIM" khi source còn trống,
        // nên thứ tự gán trong service phải bảo đảm giá trị cuối cùng là AMHS.
        assertEquals("AMHS", subject.getRejectionSource());
        verify(gwinRepository).save(subject);
    }

    @Test
    @DisplayName("CTSW114: không tra ra bản tin gốc thì vẫn phải báo Control Position")
    void testCTSW114_OrphanNdrStillReported() {
        // Bản tin có thể đã bị dọn, hoặc amss chưa ghi mts_id ngược vào gwin. Im lặng bỏ qua là
        // mất hẳn dấu vết, mà tiêu chí chấm không kèm điều kiện "nếu tra được bản tin gốc".
        McuReport report = ctsw114Ndr();

        service.processReport(report);

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_WARNING),
                contains("không tra được bản tin gốc"), eq("mtcu_report"), eq(1L));
        verify(gwinRepository, never()).save(any());
        assertEquals(McuReport.STATUS_PROCESSED, report.getStatus());
    }

    @Test
    @DisplayName("Tra bản tin gốc: gwin_id -> subject_mts_id -> subject_ipm_id")
    void testSubjectLookupOrder() {
        // 1. amss map sẵn gwin_id: tin cậy nhất, không cần tra thêm
        McuReport byId = ctsw114Ndr();
        byId.setGwinId(42L);
        when(gwinRepository.findById(42L)).thenReturn(Optional.of(subject));
        service.processReport(byId);
        verify(gwinRepository, never()).findByMtsId(anyString());

        // 2. Thiếu gwin_id thì tra MTS-Identifier - khoá chính của report
        reset(gwinRepository, alertService);
        when(gwinRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(gwinRepository.findByMtsId("MTS-114")).thenReturn(List.of(subject));
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of());
        service.processReport(ctsw114Ndr());
        verify(alertService).create(anyString(), anyString(), anyString(), eq("gwin"), eq(42L));

        // 3. Thiếu cả hai thì lùi về IPM-Identifier
        reset(gwinRepository, alertService);
        when(gwinRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());
        when(gwinRepository.findByIpmId("IPM-114")).thenReturn(List.of(subject));
        McuReport byIpm = ctsw114Ndr();
        byIpm.setSubjectMtsId(null);
        byIpm.setSubjectIpmId("IPM-114");
        service.processReport(byIpm);
        verify(alertService).create(anyString(), anyString(), anyString(), eq("gwin"), eq(42L));
    }

    @Test
    @DisplayName("DR về là tin tốt: chỉ ghi log, không đụng vào trạng thái bản tin")
    void testDeliveryReportShouldNotMarkMessageFailed() {
        when(gwinRepository.findByMtsId("MTS-114")).thenReturn(List.of(subject));
        McuReport report = ctsw114Ndr();
        report.setReportType(McuReport.TYPE_DR);
        report.setReasonCode(null);

        service.processReport(report);

        assertEquals(10, subject.getStatus());
        verify(gwinRepository, never()).save(any());
        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(conversionService).logSwimToAmhs(eq("amqp-msg-114"), eq("VVTSSWIM"), eq("OK"),
                contains("dr_received"), isNull(), eq("IPM-114"));
    }

    @Test
    @DisplayName("Mọi nhánh đều phải đánh dấu PROCESSED để không xử lý lặp")
    void testProcessedFlagAlwaysPersisted() {
        McuReport report = ctsw114Ndr();

        service.processReport(report);

        verify(reportRepository).save(report);
        assertEquals(McuReport.STATUS_PROCESSED, report.getStatus());
    }
}
