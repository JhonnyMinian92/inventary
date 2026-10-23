package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class UserDtos {
 private UserDtos(){}
 public record CreateRequest(
  @NotBlank @Size(min=3,max=80) @Pattern(regexp="^[A-Za-z0-9._-]+$",message="El usuario solo admite letras, números, punto, guion y guion bajo.") String username,
  @NotBlank @Size(min=12,max=128) String password,@NotNull UserRole role){}
 public record UpdateRequest(@NotNull UserRole role,@NotNull Boolean active){}
 public record PasswordResetRequest(@NotBlank @Size(min=12,max=128) String newPassword){}
 public record View(UUID id,String username,UserRole role,boolean active,Instant createdAt){
  static View from(AppUser user){return new View(user.getId(),user.getUsername(),UserRole.valueOf(user.getRole()),user.isActive(),user.getCreatedAt());}
 }
}
