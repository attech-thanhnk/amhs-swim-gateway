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
import vn.asg.swim.model.AmhsFeedback;
import vn.asg.swim.repository.AmhsFeedbackRepository;
import vn.asg.swim.repository.GwinRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phản hồi AMHS bay ngược về cho điện văn đã gửi ở chiều SWIM → AMHS.
 * <p>
 * Bốn test case của Appendix A trong một luồng: CTSW114 (NDR), CTSW113 (RN/NRN),
 * CTSW014 (RN priority khác SS), CTSW015 (RN lạc tuyến).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmhsFeedbackServiceTest {

    @Mock private AmhsFeedbackRepository feedbackRepository;
    @Mock private GwinRepository gwinRepository;
    @Mock private AlertService alertService;
    @Mock private MessageConversionService conversionService;

    @InjectMocks
    private AmhsFeedbackService service;

    private Gwin subject;

    @BeforeEach
    void setUp() {
        subject = new Gwin();
        subject.setMsgid(500L);
        subject.setMessageId("amqp-msg-500");
        subject.setIpmId("260906102127160Z*/CN=VVTSSWIM/");
        subject.setMtsId("MTS-500");
        subject.setOrigin("VVTSSWIM");
        subject.setAtsPriority("SS");
        // amss đặt trạng thái "đã giao" ngay khi bàn giao cho MTA - phản hồi về SAU thời điểm này
        subject.setStatus(10);

        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of());
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of());
    }

    private AmhsFeedback feedback(String type) {
        AmhsFeedback f = new AmhsFeedback();
        f.setId(7L);
        f.setIpnType(type);
        f.setOrigin("/CN=VVTSMHSA/OU=VVTS/O=VVTS/PRMD=VIETNAM/ADMD=ICAO/C=XX/");
        f.setRecipient("VVHHZTZX");
        if (AmhsFeedback.TYPE_DR.equals(type) || AmhsFeedback.TYPE_NDR.equals(type)) {
            f.setSubjectMts("MTS-500");
            f.setReasonCode("unable-to-transfer");
            f.setDiagnosticCode(null); // CTSW114: để trống là đúng chuẩn
        } else {
            f.setSubjectIpm("260906102127160Z*/CN=VVTSSWIM/");
            f.setReceiptTime("260906102152Z");
        }
        return f;
    }

    // ==================== CTSW114 - NDR ====================

    @Test
    @DisplayName("CTSW114: NDR về -> ghi log và báo Control Position")
    void testCTSW114_NdrLoggedAndReportedToControlPosition() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_ERROR),
                contains("không giao được"), eq("gwin"), eq(500L));
        verify(conversionService).logSwimToAmhs(eq("amqp-msg-500"), eq("VVTSSWIM"), eq("REJECTED"),
                contains("ndr_received"), eq("non-delivery-report"), anyString());
    }

    @Test
    @DisplayName("CTSW114: diagnostic-code ĐỂ TRỐNG là hợp lệ, không được loại bản ghi")
    void testCTSW114_EmptyDiagnosticCodeIsValid() {
        // Appendix A ghi rõ NDR của CTSW114 mang "empty field for the non-delivery-diagnostic-code".
        // Coi rỗng là dữ liệu hỏng rồi bỏ qua thì test case fail mà không để lại dấu vết.
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        for (String emptyish : new String[] { null, "", "   " }) {
            reset(alertService);
            AmhsFeedback f = feedback(AmhsFeedback.TYPE_NDR);
            f.setDiagnosticCode(emptyish);

            service.processFeedback(f);

            // Thiếu diagnostic thì lùi về reason-code, không đẩy chuỗi rỗng lên giao diện
            verify(alertService).create(anyString(), anyString(), contains("unable-to-transfer"),
                    anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("CTSW114: NDR phải sửa trạng thái điện văn gốc, không để hiển thị 'đã giao xong'")
    void testCTSW114_SubjectStatusCorrected() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        assertEquals(InboundStatus.FAILED.getValue(), subject.getStatus());
        assertEquals("non-delivery-report", subject.getRejectionReason());
        // Setter của rejectionReason tự gán "SWIM" khi source trống, nên thứ tự gán trong service
        // phải bảo đảm giá trị cuối cùng là AMHS
        assertEquals("AMHS", subject.getRejectionSource());
        verify(gwinRepository).save(subject);
    }

    @Test
    @DisplayName("CTSW114: không tra ra điện văn gốc thì vẫn phải báo Control Position")
    void testCTSW114_OrphanNdrStillReported() {
        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_WARNING),
                contains("không tra được điện văn gốc"), eq("cp"), eq(7L));
        verify(gwinRepository, never()).save(any());
    }

    @Test
    @DisplayName("DR là tin tốt: chỉ ghi log, không cảnh báo, không đụng trạng thái")
    void testDrShouldNotMarkMessageFailed() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_DR));

        assertEquals(10, subject.getStatus());
        verify(gwinRepository, never()).save(any());
        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(conversionService).logSwimToAmhs(anyString(), anyString(), eq("OK"),
                contains("dr_received"), isNull(), anyString());
    }

    // ==================== Khoá đối chiếu theo tầng sinh ra phản hồi ====================

    @Test
    @DisplayName("Report ưu tiên tra MTS-Id, IPN ưu tiên tra IPM-Id")
    void testLookupKeyDependsOnFeedbackType() {
        // MTA sinh report mà không giải mã nội dung nên chỉ biết MTS-Id;
        // người nhận sinh IPN sau khi đã mở nội dung nên biết IPM-Id.
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));
        when(gwinRepository.findByIpmId("260906102127160Z*/CN=VVTSSWIM/")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));
        verify(gwinRepository).findByMtsId("MTS-500");

        reset(gwinRepository);
        when(gwinRepository.findByIpmId("260906102127160Z*/CN=VVTSSWIM/")).thenReturn(List.of(subject));
        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));
        verify(gwinRepository).findByIpmId("260906102127160Z*/CN=VVTSSWIM/");
        verify(gwinRepository, never()).findByMtsId(anyString());
    }

    @Test
    @DisplayName("NDR mang MTS-Id trong cột subjectIPM vẫn phải tra ra điện văn gốc")
    void testReportWithMtsIdInIpmColumn() {
        // Quan sát trên dữ liệu thật 07/09: chừng nào cp.subjectMTS chưa tồn tại thì amss đặt
        // MTS-Identifier vào chính cột subjectIPM. Nếu chỉ tra ipm_id thì mọi NDR đều bị coi là
        // lạc tuyến, và Control Position mất hẳn liên kết về điện văn gốc.
        String mtsInIpmColumn = "[/PRMD=VIETNAM/ADMD=ICAO/C=XX/;gateway.attech.481-260907.084905]";
        subject.setMtsId(mtsInIpmColumn);
        when(gwinRepository.findByMtsId(mtsInIpmColumn)).thenReturn(List.of(subject));

        AmhsFeedback ndr = feedback(AmhsFeedback.TYPE_NDR);
        ndr.setSubjectMts(null);
        ndr.setSubjectIpm(mtsInIpmColumn);

        service.processFeedback(ndr);

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_ERROR),
                contains("không giao được"), eq("gwin"), eq(500L));
    }

    // ==================== CTSW113 / CTSW014 - RN, NRN ====================

    @Test
    @DisplayName("CTSW113: RN cho điện văn SS -> chấp nhận, báo Control Position")
    void testCTSW113_ValidRnReported() {
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), contains("đã được nhận"),
                eq("cp"), eq(7L));
        // Nhánh lạc tuyến (CTSW015) mới là nhánh trước đây sinh NDR - nhánh này không đi qua đó.
        verify(conversionService, never()).logSwimToAmhs(any(), anyString(), anyString(),
                contains("misrouted_ipn"), anyString(), any());
    }

    @Test
    @DisplayName("CTSW113: NRN báo điện văn KHÔNG được đọc -> cảnh báo mức cao hơn RN")
    void testCTSW113_NrnRaisesWarning() {
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));
        AmhsFeedback nrn = feedback(AmhsFeedback.TYPE_NRN);
        nrn.setNonReceiptReason(1);

        service.processFeedback(nrn);

        // NRN là tin xấu - để INFO thì operator lướt qua giữa danh sách dài
        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("KHÔNG được đọc"),
                eq("cp"), eq(7L));
    }

    @Test
    @DisplayName("CTSW014: điện văn gốc priority khác SS -> từ chối, KHÔNG sinh NDR")
    void testCTSW014_NonSsPriorityRejectedWithoutNdr() {
        subject.setAtsPriority("DD");
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("khác SS"),
                eq("cp"), eq(7L));
        // Nhánh lạc tuyến (CTSW015) mới là nhánh trước đây sinh NDR - nhánh này không đi qua đó.
        verify(conversionService, never()).logSwimToAmhs(any(), anyString(), anyString(),
                contains("misrouted_ipn"), anyString(), any());
    }

    @Test
    @DisplayName("gwin cũ chưa có ats_priority: suy lại từ AMQP priority thay vì từ chối oan")
    void testPriorityFallbackForLegacyRows() {
        // Dòng gwin ghi trước migration 2026-09-06 có ats_priority NULL. Coi NULL là "khác SS"
        // sẽ từ chối oan một RN hợp lệ - §4.5.2.2 Table 9: AMQP priority >= 6 là SS.
        subject.setAtsPriority(null);
        subject.setPriority((byte) 6);
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), contains("đã được nhận"),
                eq("cp"), eq(7L));
    }

    // ==================== CTSW015 - RN lạc tuyến ====================

    @Test
    @DisplayName("CTSW015: điện văn gốc hư cấu -> lưu lại và báo Control Position")
    void testCTSW015_MisroutedRnReportedToControlPosition() {
        AmhsFeedback rn = feedback(AmhsFeedback.TYPE_RN);
        rn.setSubjectIpm("IPM-KHONG-TON-TAI");

        service.processFeedback(rn);

        // Appendix A CTSW015: "Check the storage of the RN for appropriate action at the
        // Control Position". ITCU không sinh NDR - AMHS Component phát report ra X.400.
        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("lạc tuyến"),
                eq("cp"), eq(7L));
        verify(conversionService).logSwimToAmhs(isNull(), eq(rn.getOrigin()), eq("REJECTED"),
                contains("misrouted_ipn"), eq("misrouted-ipn"), eq("IPM-KHONG-TON-TAI"));
    }

    @Test
    @DisplayName("KHÔNG được ghi đè cp.status - đó là phân loại riêng của AMHS Component")
    void testMustNotTouchCpStatus() {
        // Quan sát dữ liệu thật 08/09: status=3 kèm ghi chú "CÓ có điện văn yêu cầu RN với ipmId
        // này", status=4 kèm "Không có...". Cột này đang hiển thị trên Control Position; ghi đè là
        // xoá mất. Tiến độ xử lý theo dõi bằng mốc cp.id ở scheduler.
        for (String type : new String[] { AmhsFeedback.TYPE_RN, AmhsFeedback.TYPE_NRN,
                AmhsFeedback.TYPE_DR, AmhsFeedback.TYPE_NDR }) {
            reset(feedbackRepository);
            service.processFeedback(feedback(type));
            verifyNoInteractions(feedbackRepository);
        }
    }

    @Test
    @DisplayName("Mọi loại đều được xử lý, không loại nào rơi qua khe")
    void testEveryTypeIsHandled() {
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));
        when(gwinRepository.findByMtsId(anyString())).thenReturn(List.of(subject));

        for (String type : new String[] { AmhsFeedback.TYPE_RN, AmhsFeedback.TYPE_NRN,
                AmhsFeedback.TYPE_DR, AmhsFeedback.TYPE_NDR }) {
            reset(conversionService);
            service.processFeedback(feedback(type));
            // Mỗi loại đều để lại dấu vết trong traffic log để Control Position tra ngược được
            verify(conversionService).logSwimToAmhs(anyString(), anyString(), anyString(),
                    anyString(), any(), any());
        }
    }
}
