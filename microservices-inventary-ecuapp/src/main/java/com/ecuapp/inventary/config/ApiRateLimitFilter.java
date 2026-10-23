package com.ecuapp.inventary.config;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ApiProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Best-effort per-instance rate limit. X-Forwarded-For is deliberately not trusted here:
 * configure the gateway/WAF for distributed limits and only use forwarded client IPs when
 * they are overwritten by a known, trusted proxy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private record Window(long startedAtMillis, int requests) {}
    private record Policy(String key, int maxRequests, long windowMillis) {}

    private final Cache<String, Window> windows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10)).maximumSize(20_000).build();
    private final ApiProblemFactory problems;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public ApiRateLimitFilter(ApiProblemFactory problems, ObjectMapper mapper, AuditService audit) {
        this.problems = problems;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        Policy policy = policy(path);
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        // Remote address is only meaningful as a client address when the proxy topology is explicit.
        String key = policy.key() + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis();
        AtomicBoolean limited = new AtomicBoolean(false);
        AtomicReference<Window> active = new AtomicReference<>();
        windows.asMap().compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAtMillis() >= policy.windowMillis()) {
                Window fresh = new Window(now, 1);
                active.set(fresh);
                return fresh;
            }
            active.set(current);
            if (current.requests() >= policy.maxRequests()) {
                limited.set(true);
                return current;
            }
            Window next = new Window(current.startedAtMillis(), current.requests() + 1);
            active.set(next);
            return next;
        });
        if (limited.get()) {
            long remaining = Math.max(1L, (active.get().startedAtMillis() + policy.windowMillis() - now + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", Long.toString(remaining));
            response.setHeader("Cache-Control", "no-store");
            var body = problems.create(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes",
                    "Se excedió el límite temporal de solicitudes.", request);
            body.setProperty("code", "RATE_LIMIT_EXCEEDED");
            mapper.writeValue(response.getOutputStream(), body);
            try {
                Object requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
                audit.recordOutcome("anonymous", "HTTP_RATE_LIMITED", "HTTP_REQUEST", null, "DENIED",
                        requestId instanceof String value ? value : null,
                        Map.of("method", safeMethod(request.getMethod()), "path", safePath(path),
                                "status", HttpStatus.TOO_MANY_REQUESTS.value(), "policy", policy.key()));
            } catch (RuntimeException ex) {
                log.warn("Could not persist rate-limit audit event; requestId={}",
                        request.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
            }
            return;
        }
        chain.doFilter(request, response);
    }

    private Policy policy(String path) {
        if ("/api/v1/auth/login".equals(path)) return new Policy("login", 10, 60_000);
        if (path.startsWith("/api/v1/integrations/whatsapp/")) return new Policy("whatsapp", 240, 60_000);
        return null;
    }

    private String safeMethod(String method) {
        return method == null ? "UNKNOWN" : method.substring(0, Math.min(method.length(), 12));
    }

    private String safePath(String path) {
        return path == null ? "/" : path.substring(0, Math.min(path.length(), 500));
    }
}
