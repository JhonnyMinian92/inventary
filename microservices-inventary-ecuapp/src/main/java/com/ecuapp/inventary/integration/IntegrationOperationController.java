package com.ecuapp.inventary.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
public class IntegrationOperationController {
    private final JdbcTemplate jdbc;

    public IntegrationOperationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record CreateOperation(@NotBlank String operationType, String partnerCode, String sourceOperationId,
                                  String correlationId, Map<String,Object> payload) {}

    public record RetryRequest(String reason) {}

    public record SubscriptionRequest(@NotBlank String name,@NotBlank String endpoint,
                                       @NotBlank String allowedEvents,String secretRef) {}

    @PostMapping("/integration-operations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('integration:manage')")
    public Map<String,Object> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                     @Valid @RequestBody CreateOperation request, Authentication auth) {
        String payload = request.payload()==null ? "{}" : request.payload().toString();
        String hash = sha256(payload);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into integration_operation(id,partner_code,operation_type,source_operation_id,idempotency_key,
            request_hash,status,correlation_id,payload,created_by)
            values (?,?,?,?,?,?,?,?,?,?)
            on conflict (idempotency_key,request_hash) do update set source_operation_id=excluded.source_operation_id
            """, id, request.partnerCode(), request.operationType(), request.sourceOperationId(), idempotencyKey.trim(),
            hash, "QUEUED", request.correlationId(), payload, auth.getName());
        UUID existing = jdbc.queryForObject("""
            select id from integration_operation where idempotency_key=? and request_hash=?
            """, UUID.class, idempotencyKey.trim(), hash);
        return Map.of("operationId", existing, "status", "QUEUED", "idempotencyKey", idempotencyKey.trim());
    }

    @GetMapping("/integration-operations/{operationId}")
    @PreAuthorize("hasAuthority('integration:read')")
    public Map<String,Object> get(@PathVariable UUID operationId) {
        return jdbc.queryForMap("""
            select id, partner_code, operation_type, source_operation_id, status, correlation_id, attempts,
                   next_attempt_at, completed_at, safe_error_code, safe_error_message, created_at
            from integration_operation where id=?
            """, operationId);
    }

    @GetMapping("/integration-operations")
    @PreAuthorize("hasAuthority('integration:read')")
    public List<Map<String,Object>> list(@RequestParam(required=false) String status,
                                         @RequestParam(defaultValue="50") int size) {
        String normalized = status==null ? null : status.trim().toUpperCase(Locale.ROOT);
        return normalized==null || normalized.isBlank()
            ? jdbc.queryForList("select id, partner_code, operation_type, status, correlation_id, attempts, next_attempt_at, created_at from integration_operation order by created_at desc limit ?", size)
            : jdbc.queryForList("select id, partner_code, operation_type, status, correlation_id, attempts, next_attempt_at, created_at from integration_operation where status=? order by created_at desc limit ?", normalized, size);
    }

    @PostMapping("/integration-operations/{operationId}/retry")
    @PreAuthorize("hasAuthority('integration:manage')")
    public Map<String,Object> retry(@PathVariable UUID operationId, @Valid @RequestBody(required=false) RetryRequest request) {
        int updated = jdbc.update("""
            update integration_operation set status='QUEUED', safe_error_code=null, safe_error_message=null,
            next_attempt_at=current_timestamp where id=? and status in ('FAILED','REJECTED','CANCELLED')
            """, operationId);
        if (updated == 0) throw new IllegalStateException("La operación no puede volver a encolarse en su estado actual.");
        return Map.of("operationId", operationId, "status", "QUEUED");
    }

    @PostMapping("/webhook-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('integration:manage')")
    public Map<String,Object> createSubscription(@Valid @RequestBody SubscriptionRequest request, Authentication auth) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into webhook_subscription(id,name,endpoint,allowed_events,secret_ref,created_by) values (?,?,?,?,?,?)",
            id,request.name(),request.endpoint(),request.allowedEvents(),request.secretRef(),auth.getName());
        return Map.of("id",id,"name",request.name(),"endpoint",request.endpoint(),"active",true);
    }

    @GetMapping("/webhook-subscriptions")
    @PreAuthorize("hasAuthority('integration:read')")
    public List<Map<String,Object>> subscriptions() {
        return jdbc.queryForList("select id,name,endpoint,allowed_events,active,created_at from webhook_subscription order by created_at desc");
    }

    @DeleteMapping("/webhook-subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('integration:manage')")
    public void deleteSubscription(@PathVariable UUID id) {
        jdbc.update("update webhook_subscription set active=false where id=?",id);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
