package com.watchtower.watchtower.controller;

import com.watchtower.watchtower.dto.ErrorResponse;
import com.watchtower.watchtower.exception.IncidentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "Validation Failed", messages));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = ex.getName() + ": invalid value '" + ex.getValue() + "'";
        return ResponseEntity.badRequest().body(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "Bad Request", List.of(message)));
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IncidentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                Instant.now(), HttpStatus.NOT_FOUND.value(), "Not Found", List.of(ex.getMessage())));
    }

    /**
     * Thrown by RemediationService when an incident's current status doesn't
     * allow the requested transition (e.g. approving an incident that isn't
     * AWAITING_APPROVAL). 409 Conflict is the correct status for "the
     * request is valid, but the resource's current state disallows it."
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                Instant.now(), HttpStatus.CONFLICT.value(), "Conflict", List.of(ex.getMessage())));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
                Instant.now(), HttpStatus.UNAUTHORIZED.value(), "Unauthorized", List.of(ex.getMessage())));
    }
}
