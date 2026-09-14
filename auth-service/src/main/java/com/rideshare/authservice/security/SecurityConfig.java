package com.rideshare.authservice.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(exception->
                        exception.authenticationEntryPoint(
                                customAuthenticationEntryPoint
                        )
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->jwt.jwtAuthenticationConverter(
                                        new JwtAuthenticationConverter()
                                )
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(
                                "/auth/register",
                                "/auth/login",
                                "/auth/service-token",
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }
}