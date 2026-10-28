package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class UserDtos {
 private UserDtos(){}
 private static final String ROLE_PATTERN="^[A-Za-z][A-Za-z0-9_]{1,39}$";
 public record CreateRequest(
  @NotBlank @Size(min=3,max=80) @Pattern(regexp="^[A-Za-z0-9._-]+$",message="El usuario solo admite letras, números, punto, guion y guion bajo.") String username,
  @NotBlank @Size(min=PasswordPolicy.MIN_LENGTH,max=PasswordPolicy.MAX_LENGTH) String password,@NotBlank @Pattern(regexp=ROLE_PATTERN,message="Código de rol inválido.") String role){}
 public record UpdateRequest(@NotBlank @Pattern(regexp=ROLE_PATTERN,message="Código de rol inválido.") String role,@NotNull Boolean active){}
 public record PasswordResetRequest(@NotBlank @Size(min=PasswordPolicy.MIN_LENGTH,max=PasswordPolicy.MAX_LENGTH) String newPassword){}
 public record View(UUID id,String username,String role,boolean active,Instant createdAt){
  static View from(AppUser user){return new View(user.getId(),user.getUsername(),user.getRole(),user.isActive(),user.getCreatedAt());}
 }
}
