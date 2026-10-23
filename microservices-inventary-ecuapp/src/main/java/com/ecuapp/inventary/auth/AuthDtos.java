package com.ecuapp.inventary.auth;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Size(max=80) String username, @NotBlank @Size(max=128) String password) {}
    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {}
    public record UserView(UUID id, String username, String role) {}
}
