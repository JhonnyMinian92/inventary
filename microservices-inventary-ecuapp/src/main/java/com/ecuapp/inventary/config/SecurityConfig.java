package com.ecuapp.inventary.config;

import com.ecuapp.inventary.auth.AppUser;
import com.ecuapp.inventary.auth.AppUserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean SecretKey jwtSecretKey(@Value("${app.security.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes.");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key) { return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
    @Bean ApplicationRunner bootstrapAdmin(AppUserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (username == null || username.isBlank() || password == null || password.isBlank()) return;
            String normalized = username.trim().toLowerCase(Locale.ROOT);
            if (users.existsByUsername(normalized)) return;
            if (password.length() < 12) throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres.");
            users.save(new AppUser(normalized, encoder.encode(password), "ADMIN"));
        };
    }
}
