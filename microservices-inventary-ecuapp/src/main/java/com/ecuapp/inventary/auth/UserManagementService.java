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
 private final AppUserRepository users;private final PasswordEncoder encoder;private final AuditService audit;
 public UserManagementService(AppUserRepository users,PasswordEncoder encoder,AuditService audit){this.users=users;this.encoder=encoder;this.audit=audit;}
 @Transactional(readOnly=true) public List<UserDtos.View> list(){return users.findAll(Sort.by(Sort.Direction.ASC,"username")).stream().map(UserDtos.View::from).toList();}
 @Transactional(readOnly=true) public UserDtos.View get(UUID id){return UserDtos.View.from(require(id));}
 @Transactional public UserDtos.View create(UserDtos.CreateRequest r,String actor){
  String username=normalize(r.username());
  if(users.existsByUsername(username))throw new ConflictException("El nombre de usuario ya existe.");
  AppUser user=users.saveAndFlush(new AppUser(username,encoder.encode(r.password()),r.role().name()));
  audit.record(actor,"USER_CREATED","APP_USER",user.getId(),Map.of("username",username,"role",r.role().name()));
  return UserDtos.View.from(user);
 }
 @Transactional public UserDtos.View update(UUID id,UserDtos.UpdateRequest r,String actor){
  AppUser user=require(id);
  if(user.getRole().equals(UserRole.ADMIN.name())&&(!r.active()||r.role()!=UserRole.ADMIN)&&users.countByRoleAndActiveTrue(UserRole.ADMIN.name())<=1)
   throw new BusinessRuleException("No se puede desactivar o cambiar el rol del último administrador activo.");
  user.updateRole(r.role().name());user.setActive(r.active());
  audit.record(actor,"USER_UPDATED","APP_USER",id,Map.of("role",r.role().name(),"active",r.active()));
  return UserDtos.View.from(user);
 }
 @Transactional public void resetPassword(UUID id,UserDtos.PasswordResetRequest r,String actor){
  AppUser user=require(id);user.updatePasswordHash(encoder.encode(r.newPassword()));
  audit.record(actor,"USER_PASSWORD_RESET","APP_USER",id,Map.of("username",user.getUsername()));
 }
 private AppUser require(UUID id){return users.findById(id).orElseThrow(()->new ResourceNotFoundException("Usuario",id));}
 private String normalize(String name){return name.trim().toLowerCase(Locale.ROOT);}
}
