package ua.edu.chnu.awards.auth.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ua.edu.chnu.awards.auth.dto.EmailRequest;
import ua.edu.chnu.awards.auth.dto.RegisterRequest;
import ua.edu.chnu.awards.auth.dto.RegistrationResponse;
import ua.edu.chnu.awards.auth.dto.TokenRequest;
import ua.edu.chnu.awards.auth.service.RegistrationService;

import lombok.RequiredArgsConstructor;

/**
 * Registration and email verification, open without a token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/verify-email")
    public RegistrationResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        return registrationService.verify(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        registrationService.resend(request.email());
    }
}
