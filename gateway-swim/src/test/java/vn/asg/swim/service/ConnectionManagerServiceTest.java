package vn.asg.swim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import vn.asg.swim.entity.Account;
import vn.asg.swim.repository.AccountRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra việc đồng bộ cột account.bind_status - đây là cột DUY NHẤT mà Control
 * Position dựa vào để hiển thị trạng thái kết nối AMQP, nên nếu nó lệch thì CP báo
 * "mất kết nối" dù broker vẫn hoạt động bình thường.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConnectionManagerServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AlertService alertService;
    @Mock private SystemLogService systemLogService;
    @Mock private org.springframework.context.ApplicationContext applicationContext;

    @InjectMocks
    private ConnectionManagerService service;

    private Account activeAccount(String bindStatus) {
        Account acc = new Account();
        acc.setId(7L);
        acc.setAccountName("swim-broker");
        acc.setProtocol("AMQP");
        acc.setStatus("ACTIVE");
        acc.setHost("192.168.22.163");
        acc.setPort(5672);
        acc.setBindStatus(bindStatus);
        when(accountRepository.findFirstByProtocolAndStatusIgnoreCase("AMQP", "ACTIVE"))
                .thenReturn(Optional.of(acc));
        when(accountRepository.findById(7L)).thenReturn(Optional.of(acc));
        return acc;
    }

    /** Giả lập trạng thái "đang kết nối tốt" mà không cần broker thật. */
    private void pretendConnected() {
        service.getConnected().set(true);
        ReflectionTestUtils.setField(service, "activeAccountId", 7L);
        ReflectionTestUtils.setField(service, "connection", mock(jakarta.jms.Connection.class));
    }

    @Test
    void testStaleDisconnectedInDb_ShouldBeResyncedToConnected() {
        // Kết nối thật vẫn tốt nhưng CSDL bị một tiến trình khác ghi đè thành DISCONNECTED
        // (ví dụ test tích hợp chạy xong và gọi @PreDestroy). Trước đây vòng rà soát chỉ tự
        // sửa khi CSDL đang là CONNECTING, nên cột này kẹt DISCONNECTED tới lần restart sau.
        Account acc = activeAccount(ConnectionManagerService.BIND_DISCONNECTED);
        pretendConnected();

        service.monitorConnectionState();

        assertEquals(ConnectionManagerService.BIND_CONNECTED, acc.getBindStatus());
        verify(accountRepository).save(acc);
    }

    @Test
    void testStaleConnectingInDb_ShouldBeResyncedToConnected() {
        Account acc = activeAccount(ConnectionManagerService.BIND_CONNECTING);
        pretendConnected();

        service.monitorConnectionState();

        assertEquals(ConnectionManagerService.BIND_CONNECTED, acc.getBindStatus());
    }

    @Test
    void testAlreadyConnectedInDb_ShouldNotWriteAgain() {
        // Trạng thái đã khớp -> không ghi CSDL mỗi 5 giây một cách vô ích
        Account acc = activeAccount(ConnectionManagerService.BIND_CONNECTED);
        pretendConnected();

        service.monitorConnectionState();

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void testDisconnectInternal_ShouldPersistDisconnectedBeforeClearingAccountId() {
        // updateBindStatus() chỉ ghi được vào CSDL khi activeAccountId còn giá trị.
        // Trước đây activeAccountId bị xóa TRƯỚC khi cập nhật, nên khi ngắt kết nối chủ động
        // CSDL vẫn treo ở CONNECTED và Control Position hiển thị sai chiều ngược lại.
        Account acc = activeAccount(ConnectionManagerService.BIND_CONNECTED);
        pretendConnected();

        service.disconnectInternal();

        assertEquals(ConnectionManagerService.BIND_DISCONNECTED, acc.getBindStatus());
        assertFalse(service.getConnected().get());
        verify(accountRepository).save(acc);
    }

    @Test
    void testNoActiveAccount_ShouldNotTouchBindStatus() {
        when(accountRepository.findFirstByProtocolAndStatusIgnoreCase("AMQP", "ACTIVE"))
                .thenReturn(Optional.empty());

        service.monitorConnectionState();

        verify(accountRepository, never()).save(any(Account.class));
    }
}
