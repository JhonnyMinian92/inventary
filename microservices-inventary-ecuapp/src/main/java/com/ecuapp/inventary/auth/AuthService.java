package com.ecuapp.inventary.auth;

import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;
    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${app.security.token-ttl-minutes:60}") long ttlMinutes) {
        this.users=users; this.passwordEncoder=passwordEncoder; this.jwtEncoder=jwtEncoder;
        this.ttlMinutes=Math.max(5,Math.min(ttlMinutes,1440));
    }
    @Transactional(readOnly=true)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        String username=request.username().trim().toLowerCase(Locale.ROOT);
        AppUser user=users.findByUsername(username).orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if(!user.isActive() || !passwordEncoder.matches(request.password(),user.getPasswordHash()))
            throw new BadCredentialsException("Credenciales inválidas");
        Instant now=Instant.now(), expires=now.plus(ttlMinutes,ChronoUnit.MINUTES);
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("inventary").issuedAt(now).expiresAt(expires)
            .subject(user.getUsername()).claim("roles",List.of("ROLE_"+user.getRole()))
            .claim("uid",user.getId().toString()).build();
        return new AuthDtos.TokenResponse(jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue(),"Bearer",expires);
    }
    @Transactional(readOnly=true)
    public AuthDtos.UserView me(String username) {
        AppUser user=users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Usuario",username));
        return new AuthDtos.UserView(user.getId(),user.getUsername(),user.getRole());
    }
}
