package com.ecuapp.inventary.config;
import com.ecuapp.inventary.auth.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.DispatcherType;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 @Bean SecretKey jwtSecretKey(@Value("${app.security.jwt-secret}") String secret){
  byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);if(bytes.length<32)throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes.");
  return new SecretKeySpec(bytes,"HmacSHA256");
 }
 @Bean JwtEncoder jwtEncoder(SecretKey key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
 @Bean JwtDecoder jwtDecoder(SecretKey key){
  NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
  OAuth2TokenValidator<Jwt> audienceValidator=jwt->jwt.getAudience().contains("inventary-api")?OAuth2TokenValidatorResult.success():
   OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required audience is missing",null));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("inventary"),audienceValidator));
  return decoder;
 }
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 private Converter<Jwt,AbstractAuthenticationToken> jwtAuthenticationConverter(AppUserRepository users,AppRoleRepository roles){
  return jwt->{
   AppUser user=users.findByUsername(jwt.getSubject()).filter(AppUser::isActive).orElseThrow(()->new BadCredentialsException("La sesión ya no es válida."));
   Object claim=jwt.getClaim("ver");if(!(claim instanceof Number version)||version.longValue()!=user.getTokenVersion())throw new BadCredentialsException("La sesión ya no es válida.");
   String role=user.getRole();
   roles.findByCodeAndActiveTrue(role).orElseThrow(()->new BadCredentialsException("La sesión ya no es válida."));
   var authorities=new java.util.LinkedHashSet<SimpleGrantedAuthority>();authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
   switch(role){case "SUPER_ADMIN"->authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));case "MANAGER_ALMACEN"->authorities.add(new SimpleGrantedAuthority("ROLE_INVENTORY_MANAGER"));case "VENDEDOR"->authorities.add(new SimpleGrantedAuthority("ROLE_SALES"));default->{}}
   roles.findActivePermissionCodes(role).stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);
   return new JwtAuthenticationToken(jwt,authorities,user.getUsername());
  };
 }
 @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.security.allowed-origins:}") String configuredOrigins){
  List<String> origins=Arrays.stream(configuredOrigins.split(",")).map(String::trim).filter(s->!s.isEmpty()).distinct().toList();
  if(origins.contains("*"))throw new IllegalStateException("CORS_ALLOWED_ORIGINS no admite el comodín *.");
  CorsConfiguration cors=new CorsConfiguration();cors.setAllowedOrigins(origins);
  cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
  cors.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key","X-Integration-Key","X-Request-Id"));
  cors.setExposedHeaders(List.of("X-Request-Id","Retry-After"));cors.setAllowCredentials(false);cors.setMaxAge(600L);
  UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",cors);return source;
 }
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AppUserRepository users,AppRoleRepository roles,ApiSecurityProblemHandler apiProblems)throws Exception{
  http.cors(Customizer.withDefaults()).csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .headers(h->h.contentTypeOptions(Customizer.withDefaults()).frameOptions(f->f.deny())
    .referrerPolicy(r->r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
    .httpStrictTransportSecurity(hsts->hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
   .authorizeHttpRequests(auth->auth
    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
    .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
    .requestMatchers(HttpMethod.GET,"/actuator/health","/actuator/health/**").permitAll()
    .requestMatchers("/actuator/info").hasAuthority("technical:read")
    .requestMatchers("/actuator/metrics","/actuator/metrics/**").hasAnyAuthority("technical:read","operations:manage")
    .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
    .requestMatchers("/api/v1/auth/**").authenticated()
    .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/v1/public/**").permitAll()
    .requestMatchers("/api/v1/integrations/whatsapp/**").permitAll()
    .requestMatchers("/api/v1/admin/**").hasAuthority("security:manage")
    .requestMatchers("/api/v1/audit-events/**").hasAuthority("audit:read")
    .requestMatchers("/api/v1/operations/**").hasAuthority("operations:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/reports/**").hasAuthority("reports:financial")
    .requestMatchers(HttpMethod.GET,"/api/v1/costing/**").hasAnyAuthority("reports:financial","costing:manage")
    .requestMatchers("/api/v1/costing/**").hasAuthority("costing:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/catalog/**").hasAuthority("catalog:read")
    .requestMatchers("/api/v1/catalog/**").hasAuthority("catalog:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/inventory/**","/api/v1/warehouses/**").hasAuthority("inventory:read")
    .requestMatchers("/api/v1/inventory/**","/api/v1/warehouses/**").hasAuthority("inventory:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/purchase-orders/**","/api/v1/goods-receipts/**","/api/v1/purchase-returns/**").hasAuthority("purchasing:read")
    .requestMatchers(HttpMethod.POST,"/api/v1/purchase-orders/*/approve").hasAuthority("purchasing:approve")
    .requestMatchers("/api/v1/purchase-orders/**","/api/v1/goods-receipts/**","/api/v1/purchase-returns/**").hasAuthority("purchasing:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/suppliers/**").hasAuthority("purchasing:read")
    .requestMatchers(HttpMethod.GET,"/api/v1/customers/**").hasAuthority("partners:read")
    .requestMatchers("/api/v1/customers/**").hasAuthority("customers:manage")
    .requestMatchers("/api/v1/suppliers/**").hasAuthority("partners:manage")
    .requestMatchers(HttpMethod.POST,"/api/v1/sales/*/approve-cancellation").hasAuthority("sales:cancel")
    .requestMatchers(HttpMethod.POST,"/api/v1/sales/*/request-cancellation").hasAuthority("sales:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/sales/**","/api/v1/sales-returns/**").hasAuthority("sales:read")
    .requestMatchers("/api/v1/sales/**","/api/v1/sales-returns/**").hasAuthority("sales:manage")
    .requestMatchers(HttpMethod.GET,"/api/v1/whatsapp-orders/**").hasAuthority("sales:read")
    .requestMatchers("/api/v1/whatsapp-orders/**").hasAuthority("sales:manage")
    .requestMatchers("/graphql").hasAuthority("catalog:read").anyRequest().denyAll())
   .exceptionHandling(errors->errors.authenticationEntryPoint(apiProblems).accessDeniedHandler(apiProblems))
   .oauth2ResourceServer(oauth->oauth.authenticationEntryPoint(apiProblems).accessDeniedHandler(apiProblems)
    .jwt(jwt->jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(users,roles))))
   .addFilterAfter(new AuthenticatedActorCaptureFilter(), BearerTokenAuthenticationFilter.class);
  return http.build();
 }
 @Bean ApplicationRunner bootstrapAdmin(AppUserRepository users,PasswordEncoder encoder,PasswordPolicy policy,
  @Value("${app.bootstrap-admin.username:}") String configuredUsername,@Value("${app.bootstrap-admin.password:}") String configuredPassword){
  return args->{
   String username=configuredUsername==null?"":configuredUsername.trim().toLowerCase(Locale.ROOT);
   String password=configuredPassword==null?"":configuredPassword;
   if(username.isEmpty()!=password.isEmpty())throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD deben configurarse juntos.");
   if(users.count()>0)return;
   if(username.isEmpty())throw new IllegalStateException("No hay usuarios. Configure BOOTSTRAP_ADMIN_USERNAME y BOOTSTRAP_ADMIN_PASSWORD para el alta inicial.");
   if(!username.matches("^[a-z0-9._-]{3,80}$"))throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME debe tener entre 3 y 80 caracteres alfanuméricos, punto, guion o guion bajo.");
   try{policy.validate(password,16);policy.validateForUser(password,username);}
   catch(RuntimeException invalid){throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD no cumple la política de contraseña inicial.",invalid);}
   users.saveAndFlush(new AppUser(username,encoder.encode(password),UserRole.SUPER_ADMIN.name()));
  };
 }
}
