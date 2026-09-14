package com.rideshare.authservice.service;

import com.rideshare.authservice.dto.LoginRequest;
import com.rideshare.authservice.dto.LoginResponse;
import com.rideshare.authservice.dto.RegisterRequest;
import com.rideshare.authservice.entity.User;
import com.rideshare.authservice.exception.InvalidCredentialException;
import com.rideshare.authservice.exception.UserAlreadyExistsException;
import com.rideshare.authservice.exception.UserDisabledException;
import com.rideshare.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public User register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request){
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(()->
                        new InvalidCredentialException(
                                "Invalid email or password"
                        )
                );

        if (!user.isEnabled()){
            throw  new UserDisabledException("User is Disabled");
        }

        if(!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )){
            throw new InvalidCredentialException("Invalid Email or Password");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token);
    }

    @Value("${application.security.service.matching-secret}")
    private String matchingSecret;

    public String generateMatchingServiceToken(String secret) {

        if (!matchingSecret.equals(secret)) {
            throw new InvalidCredentialException("Invalid service credentials");
        }

        return jwtService.generateServiceToken("matching-service");
    }
}