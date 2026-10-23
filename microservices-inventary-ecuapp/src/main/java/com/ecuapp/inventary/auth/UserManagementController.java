package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {
 private final UserManagementService service;
 public UserManagementController(UserManagementService service){this.service=service;}
 @GetMapping public List<UserDtos.View> list(){return service.list();}
 @GetMapping("/{id}") public UserDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping public UserDtos.View create(@Valid @RequestBody UserDtos.CreateRequest r,Authentication a){return service.create(r,a.getName());}
 @PatchMapping("/{id}") public UserDtos.View update(@PathVariable UUID id,@Valid @RequestBody UserDtos.UpdateRequest r,Authentication a){return service.update(id,r,a.getName());}
 @PutMapping("/{id}/password") public void resetPassword(@PathVariable UUID id,@Valid @RequestBody UserDtos.PasswordResetRequest r,Authentication a){service.resetPassword(id,r,a.getName());}
}
