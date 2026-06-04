package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.CreateAccountRequest;
import vn.asg.cp.dto.UpdateAccountRequest;
import vn.asg.cp.entity.Account;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.AccountRepository;
import java.util.Map;
import java.util.HashMap;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * CRUD /api/accounts + connect/disconnect
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountsController {

    private final AccountRepository accountRepository;

    @GetMapping
    public ResponseEntity<List<Account>> list() {
        return ResponseEntity.ok(accountRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getOne(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        return ResponseEntity.ok(account);
    }

    @PostMapping
    public ResponseEntity<Account> create(@RequestBody CreateAccountRequest request) {
        if (request.getAccountName() == null || request.getAccountName().isBlank()) {
            throw new ValidationException("account_name is required");
        }
        if (accountRepository.existsByAccountName(request.getAccountName())) {
            throw new ValidationException("account_name already exists");
        }

        Account account = new Account();
        account.setAccountName(request.getAccountName());
        account.setProtocol(request.getProtocol());
        account.setHost(request.getHost());
        account.setPort(request.getPort());
        account.setConfigJson(request.getConfigJson());
        account.setTlsEnabled(request.getTlsEnabled());
        account.setSaslMechanism(request.getSaslMechanism());
        account.setStatus("ACTIVE");
        account.setBindStatus("DISCONNECTED");

        return ResponseEntity.status(HttpStatus.CREATED).body(accountRepository.save(account));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable("id") Long id, @RequestBody UpdateAccountRequest request) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        if (request.getHost() != null)
            existing.setHost(request.getHost());
        if (request.getPort() != null)
            existing.setPort(request.getPort());
        if (request.getConfigJson() != null)
            existing.setConfigJson(request.getConfigJson());
        if (request.getTlsEnabled() != null)
            existing.setTlsEnabled(request.getTlsEnabled());
        if (request.getSaslMechanism() != null)
            existing.setSaslMechanism(request.getSaslMechanism());

        try {
            Account updated = accountRepository.save(existing);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Account updated successfully");
            response.put("data", updated);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ValidationException("Failed to update account: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("id") Long id) {
        if (!accountRepository.existsById(id)) {
            throw new ResourceNotFoundException("Account", id);
        }
        accountRepository.deleteById(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<Map<String, String>> connect(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        account.setStatus("ACTIVE");
        account.setBindStatus("CONNECTING");
        accountRepository.save(account);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account enabled. SWIM component will auto-reload configuration.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        account.setStatus("INACTIVE");
        account.setBindStatus("DISCONNECTED");
        accountRepository.save(account);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Account disabled. SWIM component will auto-reload configuration.");
        return ResponseEntity.ok(response);
    }

    /**
     * Test connection TCP socket.
     */
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable("id") Long id) {
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        String host = acc.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("Địa chỉ IP/Host của tài khoản không được để trống");
        }
        if (acc.getPort() == null) {
            throw new ValidationException("Cổng kết nối (Port) của tài khoản không được để trống");
        }
        int port = acc.getPort();

        long latencyMs = tcpPing(host, port);
        if (latencyMs < 0) {
            throw new ValidationException("Không thể vươn bộ định tuyến qua Địa chỉ IP: " + host + ":" + port + " (Timeout 2s)");
        }

        return ResponseEntity.ok(Map.of("result", "success", "latencyMs", latencyMs, "message",
                "Socket Connect tới Node " + host + " thông mạng thành công!"));
    }

    private long tcpPing(String host, int port) {
        if (host == null || host.isBlank() || port <= 0)
            return -1;
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }
}
