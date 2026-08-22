package vn.asg.swim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.asg.swim.entity.GwAlert;
import vn.asg.swim.repository.GwAlertRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock private GwAlertRepository gwAlertRepository;

    @InjectMocks
    private AlertService service;

    private GwAlert openAlert(String type) {
        GwAlert alert = new GwAlert();
        alert.setAlertType(type);
        alert.setStatus(GwAlert.STATUS_NEW);
        return alert;
    }

    @Test
    @SuppressWarnings("unchecked")
    void testResolveOpenAlerts_ShouldCloseEveryOpenAlertOfThatType() {
        // Cảnh báo CONNECTION_LOST trước đây chỉ tắt được bằng thao tác tay trên Control
        // Position, nên màn hình vẫn báo đỏ dù kết nối AMQP đã khôi phục từ lâu.
        GwAlert a1 = openAlert(GwAlert.TYPE_CONNECTION_LOST);
        GwAlert a2 = openAlert(GwAlert.TYPE_CONNECTION_LOST);
        a2.setStatus(GwAlert.STATUS_ACKNOWLEDGED);
        when(gwAlertRepository.findByAlertTypeAndStatusNot(GwAlert.TYPE_CONNECTION_LOST,
                GwAlert.STATUS_RESOLVED)).thenReturn(List.of(a1, a2));

        service.resolveOpenAlerts(GwAlert.TYPE_CONNECTION_LOST, "AMQP connection restored");

        assertEquals(GwAlert.STATUS_RESOLVED, a1.getStatus());
        assertEquals(GwAlert.STATUS_RESOLVED, a2.getStatus());
        assertNotNull(a1.getResolvedAt());
        assertNotNull(a2.getResolvedAt());

        ArgumentCaptor<List<GwAlert>> captor = ArgumentCaptor.forClass(List.class);
        verify(gwAlertRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void testResolveOpenAlerts_NothingOpen_ShouldNotWrite() {
        when(gwAlertRepository.findByAlertTypeAndStatusNot(anyString(), anyString()))
                .thenReturn(List.of());

        service.resolveOpenAlerts(GwAlert.TYPE_CONNECTION_LOST, "AMQP connection restored");

        verify(gwAlertRepository, never()).saveAll(any());
    }

    @Test
    void testResolveOpenAlerts_RepositoryFailure_ShouldNotPropagate() {
        // Việc dọn cảnh báo không được phép làm hỏng luồng kết nối lại
        when(gwAlertRepository.findByAlertTypeAndStatusNot(anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(
                () -> service.resolveOpenAlerts(GwAlert.TYPE_CONNECTION_LOST, "restored"));
    }
}
