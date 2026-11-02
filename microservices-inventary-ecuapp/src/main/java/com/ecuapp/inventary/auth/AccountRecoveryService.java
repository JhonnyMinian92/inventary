package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Email recovery uses random, single-use, hashed tokens. Only verified addresses can request
 * a reset; request responses are deliberately indistinguishable for known and unknown accounts.
 */
@Service
public class AccountRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditService audit;
    private final MeterRegistry metrics;
    private final SecureRandom random = new SecureRandom();
    private final long ttlMinutes;
    private final String frontendUrl;
    private final String from;

    public AccountRecoveryService(AppUserRepository users, JdbcTemplate jdbc, JavaMailSender mail,
            PasswordEncoder encoder, PasswordPolicy policy, AuditService audit, MeterRegistry metrics,
            @Value("${app.security.password-recovery.ttl-minutes:30}") long ttlMinutes,
            @Value("${app.security.password-recovery.frontend-url}") String frontendUrl,
            @Value("${app.security.mail-from:no-reply@inventary.local}") String from) {
        this.users=users; this.jdbc=jdbc; this.mail=mail; this.encoder=encoder; this.policy=policy;
        this.audit=audit; this.metrics=metrics; this.ttlMinutes=Math.max(5,Math.min(ttlMinutes,60));
        this.frontendUrl=frontendUrl; this.from=from;
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email=normalizeEmail(rawEmail);
        users.findByEmailIgnoreCaseAndEmailVerifiedTrue(email).filter(AppUser::isActive).ifPresent(user -> {
            String token=newToken();
            jdbc.update("UPDATE auth_password_reset SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL",user.getId());
            jdbc.update("INSERT INTO auth_password_reset(id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'))",
                    UUID.randomUUID(),user.getId(),hash(token),ttlMinutes);
            send(user.getEmail(),"Restablecer contraseña de Inventary",
                    "Para restablecer tu contraseña abre este enlace (válido durante "+ttlMinutes+" minutos):\n\n"+
                    frontendUrl+"?token="+token+"\n\nSi no solicitaste este cambio, ignora este mensaje.");
            audit.recordOutcome(user.getUsername(),"PASSWORD_RESET_REQUESTED","APP_USER",user.getId(),"SUCCESS",null,Map.of());
        });
        metrics.counter("inventary.auth.password_recovery","event","requested").increment();
    }

    @Transactional
    public void completePasswordReset(String rawToken,String newPassword) {
        String tokenHash=hash(rawToken);
        UUID userId;
        try {
            userId=jdbc.queryForObject("""
                UPDATE auth_password_reset SET consumed_at=CURRENT_TIMESTAMP
                WHERE token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP
                RETURNING user_id
                """,UUID.class,tokenHash);
        } catch (org.springframework.dao.EmptyResultDataAccessException invalid) {
            metrics.counter("inventary.auth.password_recovery","event","invalid_token").increment();
            throw new BusinessRuleException("El enlace de recuperación no es válido o ha expirado.");
        }
        AppUser user=users.findForUpdate(userId).orElseThrow(()->new BusinessRuleException("No se pudo recuperar la cuenta."));
        policy.validateForUser(newPassword,user.getUsername());
        user.updatePasswordHash(encoder.encode(newPassword));
        jdbc.update("UPDATE auth_password_reset SET consumed_at=COALESCE(consumed_at,CURRENT_TIMESTAMP) WHERE user_id=?",userId);
        audit.record(user.getUsername(),"USER_PASSWORD_RECOVERED","APP_USER",userId,Map.of());
        metrics.counter("inventary.auth.password_recovery","event","completed").increment();
    }

    @Transactional
    public void requestEmailVerification(String username,String rawEmail) {
        AppUser user=users.findByUsername(username).filter(AppUser::isActive)
                .orElseThrow(()->new BusinessRuleException("No se pudo actualizar el correo de recuperación."));
        String email=normalizeEmail(rawEmail);
        if(users.findByEmailIgnoreCase(email).filter(existing->!existing.getId().equals(user.getId())).isPresent())
            throw new BusinessRuleException("No se pudo utilizar ese correo electrónico.");
        user.setEmail(email);
        jdbc.update("UPDATE auth_email_verification SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL",user.getId());
        String token=newToken();
        jdbc.update("INSERT INTO auth_email_verification(id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP + INTERVAL '24 hours')",
                UUID.randomUUID(),user.getId(),hash(token));
        send(email,"Verifica tu correo de Inventary",
                "Confirma tu correo de recuperación con este código de un solo uso:\n\n"+token+
                "\n\nEl código vence en 24 horas. Si no solicitaste esta operación, ignora este mensaje.");
        audit.record(username,"RECOVERY_EMAIL_VERIFICATION_REQUESTED","APP_USER",user.getId(),Map.of());
    }

    @Transactional
    public void verifyEmail(String username,String rawToken) {
        AppUser user=users.findByUsername(username).filter(AppUser::isActive)
                .orElseThrow(()->new BusinessRuleException("No se pudo verificar el correo."));
        Integer consumed=jdbc.update("""
            UPDATE auth_email_verification SET consumed_at=CURRENT_TIMESTAMP
            WHERE user_id=? AND token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP
            """,user.getId(),hash(rawToken));
        if(consumed!=1) throw new BusinessRuleException("El código de verificación no es válido o ha expirado.");
        user.verifyEmail();
        audit.record(username,"RECOVERY_EMAIL_VERIFIED","APP_USER",user.getId(),Map.of());
    }

    private void send(String recipient,String subject,String body) {
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body);
            mail.send(message);
            metrics.counter("inventary.auth.email","outcome","sent").increment();
        } catch (RuntimeException failure) {
            metrics.counter("inventary.auth.email","outcome","failed").increment();
            log.error("Authentication email delivery failed; recipient omitted from log",failure);
            // Keep the public response generic to avoid account enumeration; the metric/alert carries the operational signal.
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String newToken() {
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
