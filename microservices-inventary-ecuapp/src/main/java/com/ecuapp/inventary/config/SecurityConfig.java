package com.ecuapp.inventary.config;

import com.ecuapp.inventary.auth.AppUser;
import com.ecuapp.inventary.auth.AppUserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean SecretKey jwtSecretKey(@Value("${app.security.jwt-secret}") String secret) {
  byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
  if(bytes.length<32)throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes.");
  return new SecretKeySpec(bytes,"HmacSHA256");
 }
 @Bean JwtEncoder jwtEncoder(SecretKey key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
 @Bean JwtDecoder jwtDecoder(SecretKey key){
  NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("inventary"));
  return decoder;
 }
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}

 // Las autoridades se vuelven a leer de PostgreSQL en cada solicitud: desactivar o cambiar de rol invalida los privilegios inmediatamente.
 private Converter<Jwt,AbstractAuthenticationToken> jwtAuthenticationConverter(AppUserRepository users){
  return new Converter<>() {
   @Override public AbstractAuthenticationToken convert(Jwt jwt) {
    AppUser user=users.findByUsername(jwt.getSubject()).filter(AppUser::isActive)
     .orElseThrow(()->new BadCredentialsException("El usuario asociado al token ya no está activo."));
    return new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole())),user.getUsername());
   }
  };
 }
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AppUserRepository users)throws Exception{
  http.csrf(csrf->csrf.disable())
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(auth->auth
    .requestMatchers("/actuator/health","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
    .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
    .requestMatchers("/api/v1/public/**","/graphql","/api/v1/integrations/whatsapp/**").permitAll()
    .anyRequest().authenticated())
   .oauth2ResourceServer(oauth->oauth.jwt(jwt->jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(users))));
  return http.build();
 }
 @Bean ApplicationRunner bootstrapAdmin(AppUserRepository users,PasswordEncoder encoder,
  @Value("${app.bootstrap-admin.username:}") String username,@Value("${app.bootstrap-admin.password:}") String password){
  return args->{
   if(username==null||username.isBlank()||password==null||password.isBlank())return;
   String normalized=username.trim().toLowerCase(Locale.ROOT);
   if(users.existsByUsername(normalized))return;
   if(password.length()<12)throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres.");
   users.save(new AppUser(normalized,encoder.encode(password),"ADMIN"));
  };
 }
}
