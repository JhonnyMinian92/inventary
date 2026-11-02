package com.ecuapp.inventary.config;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.ApiProblemFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final MeterRegistry metrics;
    private final JdbcTemplate jdbc;

    private final int loginPerMinute;
    private final int loginChallengePerMinute;
    private final int whatsappPerMinute;
    private final int publicPerMinute;

    public ApiRateLimitFilter(ApiProblemFactory problems, ObjectMapper mapper, AuditService audit, MeterRegistry metrics, JdbcTemplate jdbc,
                              @Value("${app.rate-limit.login-per-minute:10}") int loginPerMinute,
                              @Value("${app.rate-limit.login-challenge-per-minute:30}") int loginChallengePerMinute,
                              @Value("${app.rate-limit.whatsapp-per-minute:240}") int whatsappPerMinute,
                              @Value("${app.rate-limit.public-per-minute:300}") int publicPerMinute) {
        this.problems = problems;
        this.mapper = mapper;
        this.audit = audit;
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.loginPerMinute = loginPerMinute;
        this.loginChallengePerMinute = loginChallengePerMinute;
        this.whatsappPerMinute = whatsappPerMinute;
        this.publicPerMinute = publicPerMinute;
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
        if (isAuthenticationPolicy(policy.key())) {
            long seconds = Math.max(1, policy.windowMillis() / 1000);
            Window shared = jdbc.queryForObject("""
                INSERT INTO auth_rate_limit_window(key_hash,window_started_at,requests,updated_at)
                VALUES(?,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)
                ON CONFLICT(key_hash) DO UPDATE SET
                  requests = CASE
                    WHEN auth_rate_limit_window.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN 1
                    ELSE auth_rate_limit_window.requests + 1 END,
                  window_started_at = CASE
                    WHEN auth_rate_limit_window.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN CURRENT_TIMESTAMP
                    ELSE auth_rate_limit_window.window_started_at END,
                  updated_at = CURRENT_TIMESTAMP
                RETURNING window_started_at,requests
                """, (rs,rowNum) -> new Window(rs.getTimestamp(1).getTime(),rs.getInt(2)),
                digest(key),seconds,seconds);
            active.set(shared);
            limited.set(shared.requests() > policy.maxRequests());
        } else {
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
        }
        if (limited.get()) {
            metrics.counter("inventary.auth.rate_limited", "policy", policy.key()).increment();
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

    private boolean isAuthenticationPolicy(String key) {
        return switch (key) {
            case "login","login-challenge","password-recovery","recovery-email","mfa-management" -> true;
            default -> false;
        };
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.",impossible);
        }
    }

    private Policy policy(String path) {
        if ("/api/v1/auth/login".equals(path) || "/api/v1/auth/login-mfa".equals(path)) return new Policy("login", loginPerMinute, 60_000);
        if ("/api/v1/auth/login-challenge".equals(path)) return new Policy("login-challenge", loginChallengePerMinute, 60_000);
        if ("/api/v1/auth/password-recovery/request".equals(path) || "/api/v1/auth/password-recovery/complete".equals(path)) return new Policy("password-recovery", loginPerMinute, 60_000);
        if ("/api/v1/auth/me/email".equals(path) || "/api/v1/auth/me/email/verify".equals(path)) return new Policy("recovery-email", loginPerMinute, 60_000);
        if (path.equals("/api/v1/auth/mfa/enroll") || path.equals("/api/v1/auth/mfa/confirm") || path.equals("/api/v1/auth/mfa")) return new Policy("mfa-management", loginPerMinute, 60_000);
        if (path.startsWith("/api/v1/integrations/whatsapp/")) return new Policy("whatsapp", whatsappPerMinute, 60_000);
        if (path.startsWith("/api/v1/public/")) return new Policy("public", publicPerMinute, 60_000);
        if ("/graphql".equals(path)) return new Policy("graphql", publicPerMinute, 60_000);
        return null;
    }

    private String safeMethod(String method) {
        return method == null ? "UNKNOWN" : method.substring(0, Math.min(method.length(), 12));
    }

    private String safePath(String path) {
        return path == null ? "/" : path.substring(0, Math.min(path.length(), 500));
    }
}
