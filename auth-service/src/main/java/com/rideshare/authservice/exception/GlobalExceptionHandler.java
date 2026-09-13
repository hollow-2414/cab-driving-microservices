package com.rideshare.authservice.exception;

import com.rideshare.authservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialException ex,
            HttpServletRequest request) {

        log.warn(
                "Authentication failed for path {}: {}",
                request.getRequestURI(),
                ex.getMessage()
        );

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex,
            HttpServletRequest request) {

        log.warn(
                "Registration conflict: {}",
                ex.getMessage()
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ErrorResponse> handleUserDisabled(
            UserDisabledException ex,
            HttpServletRequest request) {

        log.warn(
                "Disabled user attempted access on path {}: {}",
                request.getRequestURI(),
                ex.getMessage()
        );

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Invalid request body parameters",
                request.getRequestURI(),
                errors
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unhandled internal exception on path {}: ",
                request.getRequestURI(),
                ex
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI(),
                null
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String error,
            String message,
            String path,
            Map<String, String> fieldErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity
                .status(status)
                .body(body);
    }
}