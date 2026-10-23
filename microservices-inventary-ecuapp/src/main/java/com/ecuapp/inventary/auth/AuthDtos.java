package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class AuthDtos {
 private AuthDtos(){}
 public record LoginRequest(@NotBlank @Size(min=3,max=80) String username,@NotBlank @Size(max=128) String password){}
 public record TokenResponse(String accessToken,String tokenType,Instant expiresAt){}
 public record UserView(UUID id,String username,String role,List<String> permissions){}
 public record ChangePasswordRequest(@NotBlank @Size(max=128) String currentPassword,@NotBlank @Size(min=12,max=128) String newPassword){}
}
