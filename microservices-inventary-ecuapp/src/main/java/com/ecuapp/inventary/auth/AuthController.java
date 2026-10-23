package com.ecuapp.inventary.auth;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service){this.service=service;}
    @PostMapping("/login") public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request){return service.login(request);}
    @GetMapping("/me") public AuthDtos.UserView me(Authentication authentication){return service.me(authentication.getName());}
}
