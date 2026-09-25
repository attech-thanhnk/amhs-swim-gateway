package vn.asg.cp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.asg.cp.dto.ApiResponse;
import vn.asg.cp.dto.CreateAccountRequest;
import vn.asg.cp.dto.UpdateAccountRequest;
import vn.asg.cp.entity.Account;
import vn.asg.cp.service.SystemHistoryService;
import vn.asg.cp.exception.ResourceNotFoundException;
import vn.asg.cp.exception.ValidationException;
import vn.asg.cp.repository.AccountRepository;
import jakarta.validation.Valid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * CRUD /api/accounts + connect/disconnect
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountsController {

    private final AccountRepository accountRepository;
    private final SystemHistoryService systemHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Account>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(accountRepository.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getOne(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        return ResponseEntity.ok(ApiResponse.ok(account));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Account>> create(@Valid @RequestBody CreateAccountRequest request) {
        if (request.getAccountName() == null || request.getAccountName().isBlank()) {
            throw new ValidationException("account_name is required");
        }
        if (accountRepository.existsByAccountName(request.getAccountName().trim())) {
            throw new ValidationException("account_name already exists");
        }
        if (request.getHost() == null || request.getHost().isBlank()) {
            throw new ValidationException("host is required");
        }
        if (request.getPort() == null) {
            throw new ValidationException("port is required");
        }
        validateAccountCredentials(request.getConfigJson());

        Account account = new Account();
        account.setAccountName(request.getAccountName().trim());
        account.setProtocol(request.getProtocol() != null ? request.getProtocol() : "AMQP");
        account.setHost(request.getHost().trim());
        account.setPort(request.getPort());
        account.setConfigJson(request.getConfigJson());
        account.setTlsEnabled(request.getTlsEnabled());
        account.setSaslMechanism(request.getSaslMechanism());
        account.setStatus("ACTIVE");
        account.setBindStatus("DISCONNECTED");

        Account savedAccount = accountRepository.save(account);

        systemHistoryService.info(
                "ACCOUNT_CREATED",
                String.format("Account '%s' created", savedAccount.getAccountName())
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account created successfully", savedAccount));
    }

    private void validateAccountCredentials(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new ValidationException("username and password are required");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(configJson);
            String username = root.has("username") && !root.get("username").isNull() ? root.get("username").asText().trim() : "";
            String password = root.has("password") && !root.get("password").isNull() ? root.get("password").asText().trim() : "";
            if (username.isBlank()) {
                throw new ValidationException("username is required");
            }
            if (password.isBlank()) {
                throw new ValidationException("password is required");
            }
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            throw new ValidationException("Invalid configuration JSON format");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> update(@PathVariable("id") Long id, @Valid @RequestBody UpdateAccountRequest request) {
        Account existing = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        String oldHost = existing.getHost();

        if (request.getHost() != null) {
            if (request.getHost().isBlank()) {
                throw new ValidationException("host is required");
            }
            existing.setHost(request.getHost().trim());
        }
        if (request.getPort() != null)
            existing.setPort(request.getPort());
        if (request.getConfigJson() != null) {
            validateAccountCredentials(request.getConfigJson());
            existing.setConfigJson(request.getConfigJson());
        }
        if (request.getTlsEnabled() != null)
            existing.setTlsEnabled(request.getTlsEnabled());
        if (request.getSaslMechanism() != null)
            existing.setSaslMechanism(request.getSaslMechanism());

        try {
            Account updated = accountRepository.save(existing);

            StringBuilder changes = new StringBuilder();
            if (!Objects.equals(oldHost, updated.getHost())) {
                changes.append(String.format("Host: %s -> %s%n", oldHost, updated.getHost()));
            }

            systemHistoryService.info(
                    "ACCOUNT_UPDATED",
                    String.format("Account '%s' updated. %s", updated.getAccountName(), changes)
            );

            return ResponseEntity.ok(ApiResponse.ok("Account updated successfully", updated));
        } catch (Exception e) {
            systemHistoryService.error(
                    "ACCOUNT_UPDATE_FAILED",
                    String.format("Failed to update account '%s'", existing.getAccountName()),
                    e.getMessage()
            );
            throw new ValidationException("Failed to update account: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        try {
            accountRepository.delete(account);
            systemHistoryService.warn(
                    "ACCOUNT_DELETED",
                    String.format("Account '%s' deleted", account.getAccountName())
            );

            return ResponseEntity.ok(ApiResponse.ok("Account deleted successfully", null));
        } catch (Exception e) {
            systemHistoryService.error(
                    "ACCOUNT_DELETE_FAILED",
                    String.format("Failed to delete account '%s'", account.getAccountName()),
                    e.getMessage()
            );
            throw new ValidationException("Failed to delete account: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/connect")
    public ResponseEntity<ApiResponse<Map<String, String>>> connect(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        account.setStatus("ACTIVE");
        account.setBindStatus("CONNECTING");
        accountRepository.save(account);

        return ResponseEntity.ok(ApiResponse.ok("Account enabled. SWIM component will auto-reload configuration.",
                Map.of("result", "success", "message", "Account enabled. SWIM component will auto-reload configuration.")));
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<ApiResponse<Map<String, String>>> disconnect(@PathVariable("id") Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        systemHistoryService.info(
                "ACCOUNT_DISCONNECTED",
                String.format("Account '%s' disconnected", account.getAccountName())
        );

        account.setStatus("INACTIVE");
        account.setBindStatus("DISCONNECTED");
        accountRepository.save(account);

        return ResponseEntity.ok(ApiResponse.ok("Account disabled. SWIM component will auto-reload configuration.",
                Map.of("result", "success", "message", "Account disabled. SWIM component will auto-reload configuration.")));
    }

    /**
     * Test connection TCP socket.
     */
    @PostMapping("/{id}/test-connection")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection(@PathVariable("id") Long id) {
        Account acc = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));

        String host = acc.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("Account host/IP is required");
        }
        if (acc.getPort() == null) {
            throw new ValidationException("Account port is required");
        }
        int port = acc.getPort();

        long latencyMs = tcpPing(host, port);
        if (latencyMs < 0) {
            throw new ValidationException("Cannot connect to host: " + host + ":" + port + " (Timeout 2s)");
        }

        return ResponseEntity.ok(ApiResponse.ok("Socket connected to node " + host + " successfully",
                Map.of("result", "success", "latencyMs", latencyMs, "message", "Socket connected to node " + host + " successfully")));
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

