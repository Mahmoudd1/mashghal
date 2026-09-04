package com.apparel.tracking.auth.web;

import java.security.Principal;

import com.apparel.tracking.auth.dto.AuthenticatedUserDto;
import com.apparel.tracking.auth.dto.LoginRequest;
import com.apparel.tracking.auth.dto.LoginResponse;
import com.apparel.tracking.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange a username and password for a JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user, for restoring a session on page load")
    public AuthenticatedUserDto me(Principal principal) {
        return authService.currentUser(principal.getName());
    }
}
