package com.ecuapp.inventary.auth;
import com.ecuapp.inventary.audit.AuditService;
import com.ecuapp.inventary.common.BusinessRuleException;
import com.ecuapp.inventary.common.ConflictException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
public class RoleManagementService {
 private final AppRoleRepository roles;private final AppPermissionRepository permissions;private final AuditService audit;
 public RoleManagementService(AppRoleRepository roles,AppPermissionRepository permissions,AuditService audit){
  this.roles=roles;this.permissions=permissions;this.audit=audit;
 }
 @Transactional(readOnly=true)
 public List<RoleDtos.RoleView> listRoles(){
  return roles.findAll(Sort.by(Sort.Direction.ASC,"code")).stream()
   .map(role->new RoleDtos.RoleView(role.getCode(),role.getDisplayName(),role.isActive(),roles.findActivePermissionCodes(role.getCode())))
   .toList();
 }
 @Transactional(readOnly=true)
 public List<RoleDtos.PermissionView> listPermissions(){
  return permissions.findByActiveTrueOrderByCodeAsc().stream()
   .map(p->new RoleDtos.PermissionView(p.getCode(),p.getDescription(),p.isActive())).toList();
 }
 @Transactional
 public RoleDtos.RoleView createRole(RoleDtos.CreateRequest request,String actor){
  String code=normalizeCode(request.code());
  if(roles.existsByCode(code))throw new ConflictException("El código de rol ya existe.");
  Set<AppPermission> granted=resolvePermissions(request.permissionCodes());
  AppRole role=roles.saveAndFlush(new AppRole(code,request.displayName().trim(),granted));
  List<String> permissionCodes=granted.stream().map(AppPermission::getCode).sorted().toList();
  audit.record(actor,"ROLE_CREATED","APP_ROLE",role.getId(),Map.of("code",code,"permissions",permissionCodes));
  return new RoleDtos.RoleView(code,role.getDisplayName(),role.isActive(),permissionCodes);
 }
 @Transactional
 public RoleDtos.RoleView replacePermissions(String roleCode,RoleDtos.ReplacePermissionsRequest request,String actor){
  String code=normalizeCode(roleCode);
  AppRole role=roles.findByCodeAndActiveTrue(code).orElseThrow(()->new ResourceNotFoundException("Rol",code));
  if("SUPER_ADMIN".equals(code))throw new BusinessRuleException("Los permisos de SUPER_ADMIN no se pueden modificar.");
  Set<AppPermission> granted=resolvePermissions(request.permissionCodes());
  role.replacePermissions(granted);
  List<String> permissionCodes=granted.stream().map(AppPermission::getCode).sorted().toList();
  audit.record(actor,"ROLE_PERMISSIONS_UPDATED","APP_ROLE",role.getId(),Map.of("permissions",permissionCodes));
  return new RoleDtos.RoleView(role.getCode(),role.getDisplayName(),role.isActive(),permissionCodes);
 }
 private Set<AppPermission> resolvePermissions(Set<String> codes){
  Set<AppPermission> result=new LinkedHashSet<>();
  for(String raw:codes){
   if(raw==null)throw new BusinessRuleException("El conjunto de permisos contiene un valor nulo.");
   String code=raw.trim().toLowerCase(Locale.ROOT);
   AppPermission permission=permissions.findByCodeAndActiveTrue(code)
    .orElseThrow(()->new BusinessRuleException("Uno o más permisos no existen o están inactivos."));
   result.add(permission);
  }
  return result;
 }
 private String normalizeCode(String code){
  String normalized=code.trim().toUpperCase(Locale.ROOT);
  if(!normalized.matches("^[A-Z][A-Z0-9_]{1,39}$"))throw new BusinessRuleException("Código de rol inválido.");
  return normalized;
 }
}
