package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/admin") @PreAuthorize("hasAuthority('security:manage')")
public class RoleManagementController {
 private final RoleManagementService service;
 public RoleManagementController(RoleManagementService service){this.service=service;}
 @GetMapping("/roles") public List<RoleDtos.RoleView> roles(){return service.listRoles();}
 @GetMapping("/roles/{code}") public RoleDtos.RoleView role(@PathVariable String code){return service.getRole(code);}
 @PostMapping("/roles") @ResponseStatus(HttpStatus.CREATED) public RoleDtos.RoleView createRole(@Valid @RequestBody RoleDtos.CreateRequest request,Authentication authentication){
  return service.createRole(request,authentication.getName());
 }
 @PatchMapping("/roles/{code}") public RoleDtos.RoleView updateRole(@PathVariable String code,@Valid @RequestBody RoleDtos.UpdateRequest request,Authentication authentication){
  return service.updateRole(code,request,authentication.getName());
 }
 @GetMapping("/permissions") public List<RoleDtos.PermissionView> permissions(){return service.listPermissions();}
 @PutMapping("/roles/{code}/permissions") public RoleDtos.RoleView replacePermissions(@PathVariable String code,
  @Valid @RequestBody RoleDtos.ReplacePermissionsRequest request,Authentication authentication){
  return service.replacePermissions(code,request,authentication.getName());
 }
}
