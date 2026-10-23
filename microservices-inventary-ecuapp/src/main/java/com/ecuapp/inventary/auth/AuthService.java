package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
@Service
public class AuthService {
 private final AppUserRepository users; private final AppRoleRepository roles; private final PasswordEncoder passwordEncoder; private final JwtEncoder jwtEncoder;
 private final PasswordPolicy passwordPolicy; private final AuditService audit; private final long ttlMinutes; private final String dummyHash;
 public AuthService(AppUserRepository users,AppRoleRepository roles,PasswordEncoder passwordEncoder,JwtEncoder jwtEncoder,PasswordPolicy passwordPolicy,AuditService audit,
  @Value("${app.security.token-ttl-minutes:60}") long ttlMinutes){
  this.users=users;this.roles=roles;this.passwordEncoder=passwordEncoder;this.jwtEncoder=jwtEncoder;this.passwordPolicy=passwordPolicy;this.audit=audit;
  this.ttlMinutes=Math.max(5,Math.min(ttlMinutes,120));
  // Keeps the response time similar for unknown users to avoid account enumeration by timing.
  this.dummyHash=passwordEncoder.encode(UUID.randomUUID().toString());
 }
 @Transactional(readOnly=true)
 public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request){
  String username=request.username().trim().toLowerCase(Locale.ROOT);
  AppUser user=users.findByUsername(username).orElse(null);
  boolean passwordMatches=passwordEncoder.matches(request.password(),user==null?dummyHash:user.getPasswordHash());
  if(user==null||!passwordMatches||!user.isActive()||roles.findByCodeAndActiveTrue(user.getRole()).isEmpty())
   throw new BadCredentialsException("Credenciales inválidas");
  Instant now=Instant.now(),expires=now.plus(ttlMinutes,ChronoUnit.MINUTES);
  JwtClaimsSet claims=JwtClaimsSet.builder().issuer("inventary").audience(List.of("inventary-api"))
   .issuedAt(now).expiresAt(expires).subject(user.getUsername()).id(UUID.randomUUID().toString())
   .claim("uid",user.getId().toString()).claim("ver",user.getTokenVersion()).build();
  JwsHeader header=JwsHeader.with(MacAlgorithm.HS256).build();
  String token=jwtEncoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
  return new AuthDtos.TokenResponse(token,"Bearer",expires);
 }
 @Transactional(readOnly=true)
 public AuthDtos.UserView me(String username){
  AppUser user=requireActive(username);
  return new AuthDtos.UserView(user.getId(),user.getUsername(),user.getRole(),roles.findActivePermissionCodes(user.getRole()).stream().sorted().toList());
 }
 @Transactional
 public void logout(String username){
  AppUser user=requireActive(username);user.invalidateTokens();
  audit.record(username,"USER_LOGOUT","APP_USER",user.getId(),Map.of("username",username));
 }
 @Transactional
 public void changePassword(String username,AuthDtos.ChangePasswordRequest request){
  AppUser user=requireActive(username);
  if(!passwordEncoder.matches(request.currentPassword(),user.getPasswordHash()))throw new BusinessRuleException("La contraseña actual no es correcta.");
  if(request.currentPassword().equals(request.newPassword()))throw new BusinessRuleException("La nueva contraseña debe ser distinta de la actual.");
  passwordPolicy.validateForUser(request.newPassword(),user.getUsername());
  user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
  audit.record(username,"USER_PASSWORD_CHANGED","APP_USER",user.getId(),Map.of("username",username));
 }
 private AppUser requireActive(String username){
  return users.findByUsername(username).filter(AppUser::isActive).orElseThrow(()->new ResourceNotFoundException("Usuario",username));
 }
}
