package com.jkymhs.backend.domain.auth.controller;

import com.jkymhs.backend.domain.auth.dto.request.EmailSendRequest;
import com.jkymhs.backend.domain.auth.dto.request.EmailVerifyRequest;
import com.jkymhs.backend.domain.auth.dto.response.EmailSendResponse;
import com.jkymhs.backend.domain.auth.dto.response.EmailVerifyResponse;
import com.jkymhs.backend.domain.auth.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/send")
    public EmailSendResponse send(@Valid @RequestBody EmailSendRequest request, HttpServletRequest httpRequest) {
        return emailVerificationService.send(request.email(), request.purpose(), httpRequest.getRemoteAddr());
    }

    @PostMapping("/verify")
    public EmailVerifyResponse verify(@Valid @RequestBody EmailVerifyRequest request) {
        return emailVerificationService.verify(request.email(), request.purpose(), request.code());
    }
}
