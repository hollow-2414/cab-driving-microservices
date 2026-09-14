package com.rideshare.locationservice.security;

import jakarta.servlet.FilterChain;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
    throws Exception{
        http.csrf(csrf ->
                csrf.disable())
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers("/actuator/health").permitAll()
                                .requestMatchers(
                                        "/api/v1/locations/drivers/nearby"
                                ).permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        new JwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }
}
