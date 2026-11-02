package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** RFC 6238 TOTP MFA with AES-GCM encrypted secrets and single-use, DB-backed login tickets. */
@Service
public class MfaService {
    public record Enrollment(String secret, String otpauthUri) {}
    private static final String BASE32="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM=new SecureRandom();
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final AuditService audit;
    private final MeterRegistry metrics;
    private final LoginAttemptGuard attempts;
    private final byte[] encryptionKey;
    private final String issuer;

    public MfaService(JdbcTemplate jdbc, AppUserRepository users, AuditService audit, MeterRegistry metrics, LoginAttemptGuard attempts,
            @Value("${app.security.mfa.encryption-key:}") String encryptionSecret,
            @Value("${app.security.mfa.issuer:Inventary}") String issuer) {
        this.jdbc=jdbc; this.users=users; this.audit=audit; this.metrics=metrics; this.attempts=attempts; this.issuer=issuer;
        try {
            this.encryptionKey=encryptionSecret==null||encryptionSecret.length()<32?null:
                MessageDigest.getInstance("SHA-256").digest(encryptionSecret.getBytes(StandardCharsets.UTF_8));
        } catch(Exception ex) { throw new IllegalStateException("No se pudo inicializar el cifrado MFA.",ex); }
    }

    @Transactional
    public Enrollment beginEnrollment(String username) {
        requireEncryptionKey();
        AppUser user=activeUser(username);
        if(isEnabled(user.getId())) throw new BusinessRuleException("La autenticación en dos pasos ya está activa. Desactívala con tu código actual antes de reinscribirla.");
        byte[] bytes=new byte[20]; RANDOM.nextBytes(bytes);
        String secret=base32(bytes);
        jdbc.update("""
            INSERT INTO auth_mfa(user_id,secret_ciphertext,enabled,created_at,enabled_at)
            VALUES(?,?,FALSE,CURRENT_TIMESTAMP,NULL)
            ON CONFLICT(user_id) DO UPDATE SET secret_ciphertext=EXCLUDED.secret_ciphertext,
              enabled=FALSE,created_at=CURRENT_TIMESTAMP,enabled_at=NULL
            """,user.getId(),encrypt(secret));
        String label=URLEncoder.encode(issuer+":"+username,StandardCharsets.UTF_8);
        String uri="otpauth://totp/"+label+"?secret="+secret+"&issuer="+URLEncoder.encode(issuer,StandardCharsets.UTF_8)+"&algorithm=SHA1&digits=6&period=30";
        audit.record(username,"MFA_ENROLLMENT_STARTED","APP_USER",user.getId(),Map.of());
        return new Enrollment(secret,uri);
    }

    @Transactional
    public void confirmEnrollment(String username,String code) {
        AppUser user=activeUser(username);
        String encrypted=jdbc.query("SELECT secret_ciphertext FROM auth_mfa WHERE user_id=?",
            rs->rs.next()?rs.getString(1):null,user.getId());
        if(encrypted==null||!verify(decrypt(encrypted),code)) {
            attempts.recordFailure(username);
            metrics.counter("inventary.auth.mfa","outcome","enrollment_failed").increment();
            throw new BusinessRuleException("El código de verificación MFA no es válido.");
        }
        jdbc.update("UPDATE auth_mfa SET enabled=TRUE,enabled_at=CURRENT_TIMESTAMP WHERE user_id=?",user.getId());
        audit.record(username,"MFA_ENABLED","APP_USER",user.getId(),Map.of());
        metrics.counter("inventary.auth.mfa","outcome","enabled").increment();
    }

    @Transactional
    public void disable(String username,String code) {
        AppUser user=activeUser(username);
        String encrypted=jdbc.query("SELECT secret_ciphertext FROM auth_mfa WHERE user_id=? AND enabled=TRUE",
            rs->rs.next()?rs.getString(1):null,user.getId());
        if(encrypted==null||!verify(decrypt(encrypted),code)) {
            attempts.recordFailure(username);
            throw new BusinessRuleException("El código MFA no es válido.");
        }
        jdbc.update("DELETE FROM auth_mfa WHERE user_id=?",user.getId());
        user.invalidateTokens();
        audit.record(username,"MFA_DISABLED","APP_USER",user.getId(),Map.of());
        metrics.counter("inventary.auth.mfa","outcome","disabled").increment();
    }

    public boolean isEnabled(UUID userId) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM auth_mfa WHERE user_id=? AND enabled=TRUE",Integer.class,userId);
        return count!=null&&count>0;
    }

    @Transactional
    public String createLoginTicket(UUID userId) {
        jdbc.update("DELETE FROM auth_mfa_login_challenge WHERE expires_at < CURRENT_TIMESTAMP - INTERVAL '1 day' OR consumed_at < CURRENT_TIMESTAMP - INTERVAL '1 day'");
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO auth_mfa_login_challenge(id,user_id,token_hash,expires_at) VALUES(?,?,?,CURRENT_TIMESTAMP + INTERVAL '5 minutes')",
                UUID.randomUUID(),userId,hash(token));
        return token;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AppUser completeLogin(String ticket,String code) {
        UUID userId;
        try {
            userId=jdbc.queryForObject("""
                UPDATE auth_mfa_login_challenge SET consumed_at=CURRENT_TIMESTAMP
                WHERE token_hash=? AND consumed_at IS NULL AND expires_at>CURRENT_TIMESTAMP
                RETURNING user_id
                """,UUID.class,hash(ticket));
        } catch(org.springframework.dao.EmptyResultDataAccessException invalid) {
            metrics.counter("inventary.auth.mfa","outcome","invalid_ticket").increment();
            throw new BadCredentialsException("El desafío MFA no es válido o ha expirado.");
        }
        String encrypted=jdbc.query("SELECT secret_ciphertext FROM auth_mfa WHERE user_id=? AND enabled=TRUE",
                rs->rs.next()?rs.getString(1):null,userId);
        if(encrypted==null||!verify(decrypt(encrypted),code)) {
            users.findById(userId).ifPresent(user -> attempts.recordFailure(user.getUsername()));
            metrics.counter("inventary.auth.mfa","outcome","invalid_code").increment();
            throw new BadCredentialsException("El código MFA no es válido.");
        }
        metrics.counter("inventary.auth.mfa","outcome","verified").increment();
        return users.findById(userId).filter(AppUser::isActive)
                .orElseThrow(()->new BadCredentialsException("La sesión ya no es válida."));
    }

    private AppUser activeUser(String username) {
        return users.findByUsername(username).filter(AppUser::isActive)
                .orElseThrow(()->new BusinessRuleException("No se pudo completar la operación MFA."));
    }
    private void requireEncryptionKey() {
        if(encryptionKey==null) throw new IllegalStateException("Configura MFA_ENCRYPTION_KEY (mínimo 32 caracteres) antes de habilitar MFA.");
    }
    private String encrypt(String secret) {
        requireEncryptionKey();
        try {
            byte[] iv=new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] result=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,result,0,iv.length); System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(Exception ex) { throw new IllegalStateException("No se pudo proteger el secreto MFA.",ex); }
    }
    private String decrypt(String value) {
        requireEncryptionKey();
        try {
            byte[] data=Base64.getDecoder().decode(value),iv=java.util.Arrays.copyOfRange(data,0,12);
            byte[] encrypted=java.util.Arrays.copyOfRange(data,12,data.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception ex) { throw new IllegalStateException("No se pudo leer el secreto MFA.",ex); }
    }
    private boolean verify(String secret,String code) {
        if(code==null||!code.matches("[0-9]{6}")) return false;
        long counter=Instant.now().getEpochSecond()/30;
        for(long offset=-1;offset<=1;offset++) if(totp(secret,counter+offset).equals(code)) return true;
        return false;
    }
    private String totp(String secret,long counter) {
        try {
            byte[] key=unbase32(secret),message=new byte[8];
            for(int i=7;i>=0;i--){message[i]=(byte)(counter&0xff);counter>>>=8;}
            Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));
            byte[] hash=mac.doFinal(message);int offset=hash[hash.length-1]&0x0f;
            int binary=((hash[offset]&0x7f)<<24)|((hash[offset+1]&0xff)<<16)|((hash[offset+2]&0xff)<<8)|(hash[offset+3]&0xff);
            return String.format(Locale.ROOT,"%06d",binary%1_000_000);
        } catch(Exception ex) { throw new IllegalStateException("No se pudo validar el código MFA.",ex); }
    }
    private String base32(byte[] data) {
        StringBuilder out=new StringBuilder();int buffer=0,bits=0;
        for(byte b:data){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){out.append(BASE32.charAt((buffer>>(bits-5))&31));bits-=5;}}
        if(bits>0)out.append(BASE32.charAt((buffer<<(5-bits))&31));
        return out.toString();
    }
    private byte[] unbase32(String value) {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();int buffer=0,bits=0;
        for(char raw:value.toUpperCase(Locale.ROOT).toCharArray()){int v=BASE32.indexOf(raw);if(v<0)continue;buffer=(buffer<<5)|v;bits+=5;if(bits>=8){out.write((buffer>>(bits-8))&255);bits-=8;}}
        return out.toByteArray();
    }
    private String hash(String token) {
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
