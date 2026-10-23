package com.ecuapp.inventary.auth;
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
import java.util.UUID;
@Service
public class AuthService {
 private final AppUserRepository users; private final PasswordEncoder passwordEncoder; private final JwtEncoder jwtEncoder; private final long ttlMinutes;
 public AuthService(AppUserRepository users,PasswordEncoder passwordEncoder,JwtEncoder jwtEncoder,@Value("${app.security.token-ttl-minutes:60}") long ttlMinutes){
  this.users=users;this.passwordEncoder=passwordEncoder;this.jwtEncoder=jwtEncoder;this.ttlMinutes=Math.max(5,Math.min(ttlMinutes,120));
 }
 @Transactional(readOnly=true)
 public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request){
  String username=request.username().trim().toLowerCase(Locale.ROOT);
  AppUser user=users.findByUsername(username).orElseThrow(()->new BadCredentialsException("Credenciales inválidas"));
  if(!user.isActive()||!passwordEncoder.matches(request.password(),user.getPasswordHash()))throw new BadCredentialsException("Credenciales inválidas");
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
  AppUser user=users.findByUsername(username).filter(AppUser::isActive).orElseThrow(()->new ResourceNotFoundException("Usuario",username));
  return new AuthDtos.UserView(user.getId(),user.getUsername(),UserRole.valueOf(user.getRole()));
 }
}
