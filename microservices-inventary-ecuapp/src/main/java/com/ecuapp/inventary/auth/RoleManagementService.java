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
@Service
public class RoleManagementService {
 private static final Set<String> RESERVED_LEGACY_CODES=Set.of("ADMIN","INVENTORY_MANAGER","SALES");
 private final AppRoleRepository roles;private final AppPermissionRepository permissions;private final AuditService audit;
 public RoleManagementService(AppRoleRepository roles,AppPermissionRepository permissions,AuditService audit){
  this.roles=roles;this.permissions=permissions;this.audit=audit;
 }
 @Transactional(readOnly=true)
 public List<RoleDtos.RoleView> listRoles(){
  return roles.findAll(Sort.by(Sort.Direction.ASC,"code")).stream().map(this::view).toList();
 }
 @Transactional(readOnly=true)
 public RoleDtos.RoleView getRole(String roleCode){return view(require(canonical(roleCode)));}
 @Transactional(readOnly=true)
 public List<RoleDtos.PermissionView> listPermissions(){
  return permissions.findByActiveTrueOrderByCodeAsc().stream()
   .map(p->new RoleDtos.PermissionView(p.getCode(),p.getDescription(),p.isActive())).toList();
 }
 @Transactional
 public RoleDtos.RoleView createRole(RoleDtos.CreateRequest request,String actor){
  String code=canonical(request.code());
  if(RESERVED_LEGACY_CODES.contains(code))throw new BusinessRuleException("El código solicitado está reservado para compatibilidad y no se puede crear.");
  if(roles.existsByCode(code))throw new ConflictException("El código de rol ya existe.");
  Set<AppPermission> granted=resolvePermissions(request.permissionCodes());
  AppRole role=roles.saveAndFlush(new AppRole(code,request.displayName().trim(),granted));
  List<String> permissionCodes=granted.stream().map(AppPermission::getCode).sorted().toList();
  audit.record(actor,"ROLE_CREATED","APP_ROLE",role.getId(),Map.of("code",code,"permissions",permissionCodes));
  return new RoleDtos.RoleView(code,role.getDisplayName(),role.isActive(),permissionCodes);
 }
 @Transactional
 public RoleDtos.RoleView updateRole(String roleCode,RoleDtos.UpdateRequest request,String actor){
  String code=canonical(roleCode);
  AppRole role=require(code);
  if("SUPER_ADMIN".equals(code)&&!request.active())throw new BusinessRuleException("El rol SUPER_ADMIN no se puede desactivar.");
  role.updateMetadata(request.displayName().trim(),request.active());
  // Users of a deactivated role lose access immediately: the JWT converter re-checks the role on each request.
  audit.record(actor,"ROLE_UPDATED","APP_ROLE",role.getId(),Map.of("code",code,"active",request.active()));
  return view(role);
 }
 @Transactional
 public RoleDtos.RoleView replacePermissions(String roleCode,RoleDtos.ReplacePermissionsRequest request,String actor){
  String code=canonical(roleCode);
  AppRole role=roles.findByCodeAndActiveTrue(code).orElseThrow(()->new ResourceNotFoundException("Rol",code));
  if("SUPER_ADMIN".equals(code))throw new BusinessRuleException("Los permisos de SUPER_ADMIN no se pueden modificar.");
  Set<AppPermission> granted=resolvePermissions(request.permissionCodes());
  role.replacePermissions(granted);
  List<String> permissionCodes=granted.stream().map(AppPermission::getCode).sorted().toList();
  audit.record(actor,"ROLE_PERMISSIONS_UPDATED","APP_ROLE",role.getId(),Map.of("permissions",permissionCodes));
  return new RoleDtos.RoleView(role.getCode(),role.getDisplayName(),role.isActive(),permissionCodes);
 }
 private RoleDtos.RoleView view(AppRole role){
  List<String> granted=role.getPermissions().stream().filter(AppPermission::isActive).map(AppPermission::getCode).sorted().toList();
  return new RoleDtos.RoleView(role.getCode(),role.getDisplayName(),role.isActive(),granted);
 }
 private AppRole require(String code){return roles.findByCode(code).orElseThrow(()->new ResourceNotFoundException("Rol",code));}
 private Set<AppPermission> resolvePermissions(Set<String> codes){
  Set<AppPermission> result=new LinkedHashSet<>();
  for(String raw:codes){
   if(raw==null)throw new BusinessRuleException("El conjunto de permisos contiene un valor nulo.");
   String code=raw.trim().toLowerCase(Locale.ROOT);
   if("security:manage".equals(code))throw new BusinessRuleException("security:manage solo puede pertenecer a SUPER_ADMIN.");
   AppPermission permission=permissions.findByCodeAndActiveTrue(code)
    .orElseThrow(()->new BusinessRuleException("Uno o más permisos no existen o están inactivos."));
   result.add(permission);
  }
  return result;
 }
 private String canonical(String code){
  String normalized=code==null?"":code.trim().toUpperCase(Locale.ROOT);
  if(!normalized.matches("^[A-Z][A-Z0-9_]{1,39}$"))throw new BusinessRuleException("Código de rol inválido.");
  return normalized;
 }
}
