package com.amoudi.financial.controller;

import com.amoudi.financial.dto.ApiResponse;
import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Controller strictly in test scope to verify validation and global exception handling behavior.
 */
@RestController
@RequestMapping("/test")
public class TestSupportController {

    public record SampleValidationRequest(
            @NotBlank(message = "Name cannot be blank")
            String name,

            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @Positive(message = "Amount must be strictly positive")
            BigDecimal amount
    ) {}

    @PostMapping("/validation")
    public ResponseEntity<ApiResponse<SampleValidationRequest>> testValidation(
            @Valid @RequestBody SampleValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(request));
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<String> testTypeMismatch(@PathVariable Long id) {
        return ResponseEntity.ok("Item ID: " + id);
    }

    @GetMapping("/not-found")
    public ResponseEntity<Void> testNotFound() {
        throw new ResourceNotFoundException("TestEntity", "test-id-123");
    }

    @GetMapping("/bad-request")
    public ResponseEntity<Void> testBadRequest() {
        throw new BadRequestException("Invalid test parameters provided");
    }

    @GetMapping("/access-denied")
    public ResponseEntity<Void> testAccessDenied() {
        throw new AccessDeniedException("User lacks necessary financial privileges");
    }

    @GetMapping("/server-error")
    public ResponseEntity<Void> testServerError() {
        throw new RuntimeException("Simulated unexpected failure");
    }
}
