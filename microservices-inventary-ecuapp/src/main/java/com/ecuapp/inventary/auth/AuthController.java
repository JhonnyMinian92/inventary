package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service){this.service=service;}
    /** Single-use ECDH P-256 key used to encrypt the credentials of exactly one login request. */
    @PostMapping("/login-challenge") public AuthDtos.LoginChallenge loginChallenge(){return service.loginChallenge();}
    @PostMapping("/login") public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request){return service.login(request);}
    @GetMapping("/me") public AuthDtos.UserView me(Authentication authentication){return service.me(authentication.getName());}
    /** Revokes every access token issued to the current user, including the one used for this request. */
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(Authentication authentication){service.logout(authentication.getName());}
    @PutMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,Authentication authentication){service.changePassword(authentication.getName(),request);}
}
