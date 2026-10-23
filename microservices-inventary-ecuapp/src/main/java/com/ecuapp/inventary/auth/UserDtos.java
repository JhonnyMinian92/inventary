package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class UserDtos {
 private UserDtos(){}
 public record CreateRequest(@NotBlank @Size(max=80) String username,@NotBlank @Size(min=12,max=128) String password,@NotNull UserRole role){}
 public record UpdateRequest(@NotNull UserRole role,@NotNull Boolean active){}
 public record PasswordResetRequest(@NotBlank @Size(min=12,max=128) String newPassword){}
 public record View(UUID id,String username,UserRole role,boolean active,Instant createdAt){
  static View from(AppUser u){return new View(u.getId(),u.getUsername(),UserRole.valueOf(u.getRole()),u.isActive(),u.getCreatedAt());}
 }
}
