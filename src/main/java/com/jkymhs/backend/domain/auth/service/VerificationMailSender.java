package com.jkymhs.backend.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationMailSender {

    private static final String SUBJECT = "[어디갈래] 이메일 인증 코드";

    private final JavaMailSender mailSender;
    private final String from;

    public VerificationMailSender(JavaMailSender mailSender, @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String to, String code) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(SUBJECT);
        message.setText("""
                어디갈래 이메일 인증 코드입니다.

                인증 코드: %s

                이 코드는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code, EmailVerificationService.CODE_TTL.toMinutes()));
        mailSender.send(message);
    }
}
