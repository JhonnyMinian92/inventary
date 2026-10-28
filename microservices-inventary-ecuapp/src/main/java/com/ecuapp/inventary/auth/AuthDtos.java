package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public final class AuthDtos {
 private AuthDtos(){}
 /**
  * Either {@code encrypted} (required by default) or plaintext username/password when
  * app.security.login-encryption.required=false. Field contents are validated once resolved into {@link Credentials}.
  */
 public record LoginRequest(@Size(max=80) String username,@Size(max=128) String password,@Valid EncryptedCredentials encrypted){
  @Override public String toString(){return "LoginRequest[username="+username+", password=***, encrypted="+(encrypted!=null)+"]";}
 }
 /** Output of the client side of {@link LoginEncryptionService#ALGORITHM}, all binary fields in standard Base64. */
 public record EncryptedCredentials(@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9_-]+") String challengeId,
  @NotBlank @Size(max=200) String publicKey,@NotBlank @Size(max=24) String iv,@NotBlank @Size(max=2048) String ciphertext){}
 public record Credentials(@NotBlank @Size(min=3,max=80) String username,@NotBlank @Size(max=128) String password){
  @Override public String toString(){return "Credentials[username="+username+", password=***]";}
 }
 public record LoginChallenge(String challengeId,String algorithm,String publicKey,Instant expiresAt){}
 public record TokenResponse(String accessToken,String tokenType,Instant expiresAt){}
 public record UserView(UUID id,String username,String role,List<String> permissions){}
 public record ChangePasswordRequest(@NotBlank @Size(max=128) String currentPassword,@NotBlank @Size(min=PasswordPolicy.MIN_LENGTH,max=PasswordPolicy.MAX_LENGTH) String newPassword){
  @Override public String toString(){return "ChangePasswordRequest[***]";}
 }
}
