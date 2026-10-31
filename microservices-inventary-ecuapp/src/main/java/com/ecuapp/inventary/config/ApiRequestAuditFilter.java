package com.ecuapp.inventary.config;

import com.ecuapp.inventary.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Audits failed API responses and successful reads of sensitive business data.
 *
 * The filter deliberately does not inspect request bodies, query parameters, response bodies,
 * cookies or authentication headers. Successful business writes are audited by their domain services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiRequestAuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRequestAuditFilter.class);
    private static final List<String> SENSITIVE_READ_ROOTS = List.of(
        "/api/v1/admin",
        "/api/v1/audit-events",
        "/api/v1/reports",
        "/api/v1/costing",
        "/api/v1/customers",
        "/api/v1/suppliers",
        "/api/v1/sales",
        "/api/v1/sales-returns",
        "/api/v1/purchase-orders",
        "/api/v1/goods-receipts",
        "/api/v1/purchase-returns",
        "/api/v1/operations",
        "/api/v1/inventory/movements",
        "/api/v1/inventory/counts",
        "/api/v1/inventory/balances",
        "/api/v1/warehouses"
    );

    private final AuditService auditService;

    public ApiRequestAuditFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            String path = request.getRequestURI();
            if (path == null || !path.startsWith("/api/")) {
                return;
            }

            try {
                String actor = authenticatedActor(request);
                String requestId = requestId(request);
                if (status >= 400) {
                    String outcome = status == 401 || status == 403 ? "DENIED" : "FAILURE";
                    auditService.recordOutcome(actor, "HTTP_REQUEST_FAILED", "HTTP_REQUEST", null, outcome,
                        requestId, Map.of("method", safeMethod(request.getMethod()),
                            "path", safePath(path), "status", status));
                } else if (status >= 200 && status < 300 && isSensitiveRead(request.getMethod(), path)) {
                    auditService.recordOutcome(actor, "SENSITIVE_DATA_READ", "HTTP_REQUEST", null, "SUCCESS",
                        requestId, Map.of("method", safeMethod(request.getMethod()),
                            "path", safePath(path), "status", status));
                }
            } catch (RuntimeException failure) {
                log.warn("Could not persist HTTP audit event; requestId={}", requestId(request));
            }
        }
    }

    private String authenticatedActor(HttpServletRequest request) {
        Object capturedActor = request.getAttribute(AuthenticatedActorCaptureFilter.ATTRIBUTE);
        if (capturedActor instanceof String value && !value.isBlank()) return value;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && !"anonymousUser".equals(authentication.getName())) return authentication.getName();
        return "anonymous";
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        return value instanceof String text ? text : null;
    }

    private boolean isSensitiveRead(String method, String path) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return SENSITIVE_READ_ROOTS.stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }

    private String safeMethod(String method) {
        return method == null ? "UNKNOWN" : method.substring(0, Math.min(method.length(), 12));
    }

    private String safePath(String path) {
        return path == null ? "/" : path.substring(0, Math.min(path.length(), 500));
    }
}
