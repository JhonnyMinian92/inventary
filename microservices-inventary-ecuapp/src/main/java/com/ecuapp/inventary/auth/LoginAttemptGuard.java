package com.ecuapp.inventary.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** Distributed account lockout backed by PostgreSQL; all replicas share one atomic UPSERT state. */
@Component
public class LoginAttemptGuard {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptGuard(JdbcTemplate jdbc, MeterRegistry metrics,
            @Value("${app.security.login-lockout.max-failures:5}") int maxFailures,
            @Value("${app.security.login-lockout.minutes:15}") long minutes) {
        this.jdbc = jdbc;
        this.metrics = metrics;
        this.maxFailures = Math.max(0, maxFailures);
        this.window = Duration.ofMinutes(Math.max(1, Math.min(minutes, 1440)));
    }

    public void checkNotLocked(String username) {
        if (maxFailures <= 0) return;
        Instant until = jdbc.query(
            "SELECT locked_until FROM auth_login_attempt WHERE subject_hash=?",
            rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null,
            digest(username));
        if (until != null && until.isAfter(Instant.now())) {
            long seconds = Math.max(1, Duration.between(Instant.now(), until).toSeconds());
            metrics.counter("inventary.auth.lockouts", "source", "account").increment();
            throw LoginSecurityException.locked(seconds);
        }
    }

    /** Returns true if this failed attempt activated a lockout. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(String username) {
        metrics.counter("inventary.auth.login", "outcome", "failure").increment();
        if (maxFailures <= 0) return false;
        String sql = """
            INSERT INTO auth_login_attempt(subject_hash, failures, window_started_at, locked_until, updated_at)
            VALUES (?, 1, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
            ON CONFLICT(subject_hash) DO UPDATE SET
              failures = CASE
                WHEN auth_login_attempt.locked_until IS NOT NULL AND auth_login_attempt.locked_until <= CURRENT_TIMESTAMP THEN 1
                WHEN auth_login_attempt.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') THEN 1
                ELSE auth_login_attempt.failures + 1 END,
              window_started_at = CASE
                WHEN (auth_login_attempt.locked_until IS NOT NULL AND auth_login_attempt.locked_until <= CURRENT_TIMESTAMP)
                  OR auth_login_attempt.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                THEN CURRENT_TIMESTAMP ELSE auth_login_attempt.window_started_at END,
              locked_until = CASE
                WHEN (CASE
                  WHEN (auth_login_attempt.locked_until IS NOT NULL AND auth_login_attempt.locked_until <= CURRENT_TIMESTAMP)
                    OR auth_login_attempt.window_started_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                  THEN 1 ELSE auth_login_attempt.failures + 1 END) >= ?
                THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 second') ELSE NULL END,
              updated_at = CURRENT_TIMESTAMP
            RETURNING locked_until IS NOT NULL AND locked_until > CURRENT_TIMESTAMP
            """;
        Boolean locked = jdbc.queryForObject(sql, Boolean.class, digest(username),
                window.toSeconds(), window.toSeconds(), window.toSeconds(), maxFailures, window.toSeconds());
        if (Boolean.TRUE.equals(locked)) {
            metrics.counter("inventary.auth.lockouts", "source", "account").increment();
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String username) {
        jdbc.update("DELETE FROM auth_login_attempt WHERE subject_hash=?", digest(username));
        metrics.counter("inventary.auth.login", "outcome", "success").increment();
    }

    private String digest(String username) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(username.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible);
        }
    }
}
