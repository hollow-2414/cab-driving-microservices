package com.rideshare.authservice.controller;

import com.rideshare.authservice.dto.LoginRequest;
import com.rideshare.authservice.dto.LoginResponse;
import com.rideshare.authservice.dto.RegisterRequest;
import com.rideshare.authservice.entity.User;
import com.rideshare.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("User registered successfully with id: " + user.getId());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
            ){
        System.out.println(">>> LOGIN CONTROLLER REACHED");
        return ResponseEntity.ok(authService.login(request));    }
}