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
            f.setDiagnosticCode(null);
        } else {
            f.setSubjectIpm("260906102127160Z*/CN=VVTSSWIM/");
            f.setReceiptTime("260906102152Z");
        }
        return f;
    }

    // ==================== NDR ====================

    @Test
    @DisplayName("NDR về -> ghi log và báo Control Position")
    void testCTSW114_NdrLoggedAndReportedToControlPosition() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_ERROR),
                contains("không giao được"), eq("gwin"), eq(500L));
        verify(conversionService).logSwimToAmhs(eq("amqp-msg-500"), eq("VVTSSWIM"), eq("REJECTED"),
                contains("ndr_received"), eq("non-delivery-report"), anyString());
    }

    @Test
    @DisplayName("NDR: diagnostic-code để trống vẫn hợp lệ")
    void testCTSW114_EmptyDiagnosticCodeIsValid() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        for (String emptyish : new String[] { null, "", "   " }) {
            reset(alertService);
            AmhsFeedback f = feedback(AmhsFeedback.TYPE_NDR);
            f.setDiagnosticCode(emptyish);

            service.processFeedback(f);

            verify(alertService).create(anyString(), anyString(), contains("unable-to-transfer"),
                    anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("NDR cập nhật trạng thái điện văn gốc")
    void testCTSW114_SubjectStatusCorrected() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        assertEquals(InboundStatus.FAILED.getValue(), subject.getStatus());
        assertEquals("non-delivery-report", subject.getRejectionReason());
        assertEquals("AMHS", subject.getRejectionSource());
        verify(gwinRepository).save(subject);
    }

    @Test
    @DisplayName("NDR không tra ra điện văn gốc vẫn báo Control Position")
    void testCTSW114_OrphanNdrStillReported() {
        service.processFeedback(feedback(AmhsFeedback.TYPE_NDR));

        verify(alertService).create(eq(GwAlert.TYPE_MESSAGE_DEAD), eq(GwAlert.SEV_WARNING),
                contains("không tra được điện văn gốc"), eq("cp"), eq(7L));
        verify(gwinRepository, never()).save(any());
    }

    @Test
    @DisplayName("DR chỉ ghi log, không cảnh báo, không đổi trạng thái")
    void testDrShouldNotMarkMessageFailed() {
        when(gwinRepository.findByMtsId("MTS-500")).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_DR));

        assertEquals(10, subject.getStatus());
        verify(gwinRepository, never()).save(any());
        verify(alertService, never()).create(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(conversionService).logSwimToAmhs(anyString(), anyString(), eq("OK"),
                contains("dr_received"), isNull(), anyString());
    }

    // ==================== Tra cứu ====================

    @Test
    @DisplayName("Report ưu tiên tra MTS-Id, IPN ưu tiên tra IPM-Id")
    void testLookupKeyDependsOnFeedbackType() {
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
    @DisplayName("NDR mang MTS-Id trong cột subjectIPM vẫn tra ra điện văn gốc")
    void testReportWithMtsIdInIpmColumn() {
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

    // ==================== RN / NRN ====================

    @Test
    @DisplayName("RN cho điện văn SS -> chấp nhận, báo Control Position")
    void testCTSW113_ValidRnReported() {
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), contains("đã được nhận"),
                eq("cp"), eq(7L));
        verify(conversionService, never()).logSwimToAmhs(any(), anyString(), anyString(),
                contains("misrouted_ipn"), anyString(), any());
    }

    @Test
    @DisplayName("NRN báo điện văn không được đọc -> cảnh báo WARNING")
    void testCTSW113_NrnRaisesWarning() {
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));
        AmhsFeedback nrn = feedback(AmhsFeedback.TYPE_NRN);
        nrn.setNonReceiptReason(1);

        service.processFeedback(nrn);

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("KHÔNG được đọc"),
                eq("cp"), eq(7L));
    }

    @Test
    @DisplayName("Điện văn gốc priority khác SS -> từ chối, không sinh NDR")
    void testCTSW014_NonSsPriorityRejectedWithoutNdr() {
        subject.setAtsPriority("DD");
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("khác SS"),
                eq("cp"), eq(7L));
        verify(conversionService, never()).logSwimToAmhs(any(), anyString(), anyString(),
                contains("misrouted_ipn"), anyString(), any());
    }

    @Test
    @DisplayName("gwin cũ chưa có ats_priority: suy lại từ AMQP priority")
    void testPriorityFallbackForLegacyRows() {
        subject.setAtsPriority(null);
        subject.setPriority((byte) 6);
        when(gwinRepository.findByIpmId(anyString())).thenReturn(List.of(subject));

        service.processFeedback(feedback(AmhsFeedback.TYPE_RN));

        verify(alertService).create(anyString(), eq(GwAlert.SEV_INFO), contains("đã được nhận"),
                eq("cp"), eq(7L));
    }

    // ==================== RN lạc tuyến ====================

    @Test
    @DisplayName("RN lạc tuyến -> lưu lại và báo Control Position")
    void testCTSW015_MisroutedRnReportedToControlPosition() {
        AmhsFeedback rn = feedback(AmhsFeedback.TYPE_RN);
        rn.setSubjectIpm("IPM-KHONG-TON-TAI");

        service.processFeedback(rn);

        verify(alertService).create(anyString(), eq(GwAlert.SEV_WARNING), contains("lạc tuyến"),
                eq("cp"), eq(7L));
        verify(conversionService).logSwimToAmhs(isNull(), eq(rn.getOrigin()), eq("REJECTED"),
                contains("misrouted_ipn"), eq("misrouted-ipn"), eq("IPM-KHONG-TON-TAI"));
    }

    @Test
    @DisplayName("Không ghi đè cp.status")
    void testMustNotTouchCpStatus() {
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
