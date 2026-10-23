package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/users") @PreAuthorize("hasAuthority('security:manage')")
public class UserManagementController {
 private final UserManagementService service;
 public UserManagementController(UserManagementService service){this.service=service;}
 @GetMapping public Page<UserDtos.View> list(@PageableDefault(size=20,sort="username") Pageable pageable){return service.list(pageable);}
 @GetMapping("/{id}") public UserDtos.View get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserDtos.View create(@Valid @RequestBody UserDtos.CreateRequest request,Authentication authentication){return service.create(request,authentication.getName());}
 @PatchMapping("/{id}") public UserDtos.View update(@PathVariable UUID id,@Valid @RequestBody UserDtos.UpdateRequest request,Authentication authentication){return service.update(id,request,authentication.getName());}
 @PutMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void resetPassword(@PathVariable UUID id,@Valid @RequestBody UserDtos.PasswordResetRequest request,Authentication authentication){service.resetPassword(id,request,authentication.getName());}
}
