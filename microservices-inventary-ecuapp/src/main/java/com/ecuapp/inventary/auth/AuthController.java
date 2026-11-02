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
    @PostMapping("/password-recovery/request") @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordRecovery(@Valid @RequestBody RecoveryRequest request){recovery.requestPasswordReset(request.email());}
    @PostMapping("/password-recovery/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePasswordRecovery(@Valid @RequestBody ResetPasswordRequest request){recovery.completePasswordReset(request.token(),request.newPassword());}
    @PutMapping("/me/email") @ResponseStatus(HttpStatus.ACCEPTED)
    public void setRecoveryEmail(@Valid @RequestBody EmailRequest request, Authentication authentication){recovery.requestEmailVerification(authentication.getName(),request.email());}
    @PostMapping("/me/email/verify") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyRecoveryEmail(@Valid @RequestBody VerifyEmailRequest request, Authentication authentication){recovery.verifyEmail(authentication.getName(),request.token());}
    @GetMapping("/me") public AuthDtos.UserView me(Authentication authentication){return service.me(authentication.getName());}
    /** Revokes every access token issued to the current user, including the one used for this request. */
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(Authentication authentication){service.logout(authentication.getName());}
    public record RecoveryRequest(@jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=254) String email){}
    public record ResetPasswordRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) String token,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=PasswordPolicy.MIN_LENGTH,max=PasswordPolicy.MAX_LENGTH) String newPassword){}
    public record EmailRequest(@jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=254) String email){}
    public record VerifyEmailRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) String token){}
    @PutMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request,Authentication authentication){service.changePassword(authentication.getName(),request);}
}
