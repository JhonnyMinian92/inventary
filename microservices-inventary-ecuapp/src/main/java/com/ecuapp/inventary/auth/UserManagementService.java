package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
@Service
public class UserManagementService {
 private final AppUserRepository users;private final PasswordEncoder encoder;private final AuditService audit;private final PasswordPolicy passwordPolicy;
 public UserManagementService(AppUserRepository users,PasswordEncoder encoder,AuditService audit,PasswordPolicy passwordPolicy){
  this.users=users;this.encoder=encoder;this.audit=audit;this.passwordPolicy=passwordPolicy;
 }
 @Transactional(readOnly=true) public List<UserDtos.View> list(){return users.findAll(Sort.by(Sort.Direction.ASC,"username")).stream().map(UserDtos.View::from).toList();}
 @Transactional(readOnly=true) public UserDtos.View get(UUID id){return UserDtos.View.from(require(id));}
 @Transactional public UserDtos.View create(UserDtos.CreateRequest request,String actor){
  String username=normalize(request.username());passwordPolicy.validateForUser(request.password(),username);
  if(users.existsByUsername(username))throw new ConflictException("El nombre de usuario ya existe.");
  AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(request.password()),request.role().name()));
  audit.record(actor,"USER_CREATED","APP_USER",user.getId(),Map.of("username",username,"role",request.role().name()));
  return UserDtos.View.from(user);
 }
 @Transactional public UserDtos.View update(UUID id,UserDtos.UpdateRequest request,String actor){
  AppUser user=require(id);
  boolean removingAdmin=UserRole.SUPER_ADMIN.name().equals(user.getRole())&&(!request.active()||request.role()!=UserRole.SUPER_ADMIN);
  if(removingAdmin){
   // Stable, pessimistic locking serializes concurrent attempts to remove the last active administrator.
   users.findByRoleAndActiveTrueOrderByIdAsc(UserRole.SUPER_ADMIN.name());
   if(users.countByRoleAndActiveTrue(UserRole.SUPER_ADMIN.name())<=1)
    throw new BusinessRuleException("No se puede desactivar o degradar al último administrador activo.");
  }
  user.updateRole(request.role().name());user.setActive(request.active());
  audit.record(actor,"USER_UPDATED","APP_USER",id,Map.of("role",request.role().name(),"active",request.active()));
  return UserDtos.View.from(user);
 }
 @Transactional public void resetPassword(UUID id,UserDtos.PasswordResetRequest request,String actor){
  AppUser user=require(id);passwordPolicy.validateForUser(request.newPassword(),user.getUsername());user.updatePasswordHash(encoder.encode(request.newPassword()));
  audit.record(actor,"USER_PASSWORD_RESET","APP_USER",id,Map.of("username",user.getUsername()));
 }
 private AppUser require(UUID id){return users.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario",id));}
 private String normalize(String username){return username.trim().toLowerCase(Locale.ROOT);}
}
