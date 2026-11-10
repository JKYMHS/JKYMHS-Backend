package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.entity.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 방문 확인 메일 내용을 만들어 보낸다. 인증 메일(VerificationMailSender)과 같은 JavaMailSender·보낸 사람 주소를 쓴다.
 */
@Component
public class VisitCheckMailSender {

    static final String SUBJECT = "[어디 갈래] %s, 다녀오셨나요?";

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;
    private final String from;

    public VisitCheckMailSender(JavaMailSender mailSender, AppProperties appProperties,
                                @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
        this.from = from;
    }

    public void send(String to, Trip trip) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(SUBJECT.formatted(trip.getTitle()));
        message.setText("""
                %s 여행은 즐거우셨나요?

                다녀온 장소와 만족도를 남겨주시면 다음 추천에 반영할게요.
                %s

                이 메일은 이메일 알림에 동의한 회원에게만 보냅니다. 마이페이지에서 언제든 끌 수 있어요.
                """.formatted(trip.getTitle(), appProperties.frontendUrl("/trips/" + trip.getId() + "?visitCheck=1")));
        mailSender.send(message);
    }
}
