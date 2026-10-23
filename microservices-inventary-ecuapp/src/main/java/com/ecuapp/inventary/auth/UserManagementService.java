package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.Pages;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
public class UserManagementService {
 private static final Set<String> SORT=Set.of("username","role","active","createdAt");
 private final AppUserRepository users;private final AppRoleRepository roles;private final PasswordEncoder encoder;private final AuditService audit;private final PasswordPolicy passwordPolicy;
 public UserManagementService(AppUserRepository users,AppRoleRepository roles,PasswordEncoder encoder,AuditService audit,PasswordPolicy passwordPolicy){
  this.users=users;this.roles=roles;this.encoder=encoder;this.audit=audit;this.passwordPolicy=passwordPolicy;
 }
 @Transactional(readOnly=true) public Page<UserDtos.View> list(Pageable pageable){return users.findAll(Pages.of(pageable,SORT,Sort.by("username"))).map(UserDtos.View::from);}
 @Transactional(readOnly=true) public UserDtos.View get(UUID id){return UserDtos.View.from(require(id));}
 @Transactional public UserDtos.View create(UserDtos.CreateRequest request,String actor){
  String username=normalize(request.username());passwordPolicy.validateForUser(request.password(),username);
  String role=activeRole(request.role());
  if(users.existsByUsername(username))throw new ConflictException("El nombre de usuario ya existe.");
  AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(request.password()),role));
  audit.record(actor,"USER_CREATED","APP_USER",user.getId(),Map.of("username",username,"role",role));
  return UserDtos.View.from(user);
 }
 @Transactional public UserDtos.View update(UUID id,UserDtos.UpdateRequest request,String actor){
  String role=activeRole(request.role());
  // The membership lock is taken before reading the user so concurrent demotions are evaluated one after another.
  users.lockSuperAdminMembership();
  AppUser user=require(id);
  boolean removingAdmin=UserRole.SUPER_ADMIN.name().equals(user.getRole())&&user.isActive()&&(!request.active()||!UserRole.SUPER_ADMIN.name().equals(role));
  if(removingAdmin&&users.countByRoleAndActiveTrue(UserRole.SUPER_ADMIN.name())<=1)
   throw new BusinessRuleException("No se puede desactivar o degradar al último administrador activo.");
  user.updateRole(role);user.setActive(request.active());
  users.flush();
  audit.record(actor,"USER_UPDATED","APP_USER",id,Map.of("role",role,"active",request.active()));
  return UserDtos.View.from(user);
 }
 @Transactional public void resetPassword(UUID id,UserDtos.PasswordResetRequest request,String actor){
  AppUser user=require(id);passwordPolicy.validateForUser(request.newPassword(),user.getUsername());user.updatePasswordHash(encoder.encode(request.newPassword()));
  audit.record(actor,"USER_PASSWORD_RESET","APP_USER",id,Map.of("username",user.getUsername()));
 }
 private String activeRole(String raw){
  String code=raw.trim().toUpperCase(Locale.ROOT);
  return roles.findByCodeAndActiveTrue(code).map(AppRole::getCode).orElseThrow(()->new BusinessRuleException("El rol indicado no existe o está inactivo."));
 }
 private AppUser require(UUID id){return users.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario",id));}
 private String normalize(String username){return username.trim().toLowerCase(Locale.ROOT);}
}
