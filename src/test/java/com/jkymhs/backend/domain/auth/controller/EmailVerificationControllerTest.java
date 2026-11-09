package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.service.AuthRedisKeys;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 메일 발송·검증 API를 실제 MySQL·Redis와 함께 호출해본다.
 * 메일 서버만 가짜(JavaMailSender 목)로 바꿔서, 보낸 메일 내용을 꺼내 확인한다.
 */
class EmailVerificationControllerTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String DEFAULT_IP = "127.0.0.1";

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("발송에 성공하면 코드를 5분 TTL로 저장하고, 같은 코드를 메일로 보낸다")
    void sendSuccess() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.resendAfter").value(60));

        SimpleMailMessage mail = sentMail();
        String code = savedCode(VerificationPurpose.SIGNUP, EMAIL);
        assertThat(code).matches("\\d{6}");
        assertThat(mail.getTo()).containsExactly(EMAIL);
        assertThat(mail.getSubject()).isEqualTo("[어디갈래] 이메일 인증 코드");
        assertThat(mail.getText()).contains(code).contains("5분");

        String key = AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL);
        assertThat(redisTemplate.getExpire(key)).isBetween(290L, 300L);
        assertThat(redisTemplate.opsForHash().get(key, "failCount")).isEqualTo("0");
        assertThat(redisTemplate.opsForHash().get(key, "sentAt")).isNotNull();
    }

    @Test
    @DisplayName("이메일 앞뒤 공백과 대문자는 정리해서 저장하고 보낸다")
    void normalizeEmail() throws Exception {
        send("  User@Example.COM ", VerificationPurpose.SIGNUP)
                .andExpect(status().isOk());

        assertThat(sentMail().getTo()).containsExactly(EMAIL);
        assertThat(savedCode(VerificationPurpose.SIGNUP, EMAIL)).isNotNull();
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400으로 응답하고 메일을 보내지 않는다")
    void invalidEmail() throws Exception {
        send("not-an-email", VerificationPurpose.SIGNUP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 이메일 형식이 아닙니다."));

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("purpose가 SIGNUP / RESET_PASSWORD가 아니면 400으로 응답한다")
    void invalidPurpose() throws Exception {
        mockMvc.perform(post("/api/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "purpose": "LOGIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("SIGNUP인데 이미 가입된 이메일이면 409로 응답한다")
    void signupDuplicateEmail() throws Exception {
        userRepository.save(User.createWithPassword(EMAIL, "hashed"));

        send(EMAIL, VerificationPurpose.SIGNUP)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("RESET_PASSWORD는 가입된 이메일이면 메일을 보낸다")
    void resetPasswordRegistered() throws Exception {
        userRepository.save(User.createWithPassword(EMAIL, "hashed"));

        send(EMAIL, VerificationPurpose.RESET_PASSWORD)
                .andExpect(status().isOk());

        assertThat(sentMail().getText()).contains(savedCode(VerificationPurpose.RESET_PASSWORD, EMAIL));
    }

    @Test
    @DisplayName("RESET_PASSWORD는 가입되지 않은 이메일이어도 같은 200을 주고, 코드는 저장하지만 메일은 보내지 않는다")
    void resetPasswordUnregistered() throws Exception {
        send(EMAIL, VerificationPurpose.RESET_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.resendAfter").value(60));

        verifyNoInteractions(mailSender);
        assertThat(savedCode(VerificationPurpose.RESET_PASSWORD, EMAIL)).isNotNull();
        // 가입된 이메일과 똑같이 60초 재전송 제한도 걸린다
        send(EMAIL, VerificationPurpose.RESET_PASSWORD)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("60초 안에 같은 이메일·용도로 다시 보내면 429로 응답한다")
    void resendCooldown() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());

        send(EMAIL, VerificationPurpose.SIGNUP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.description").value("60초 재전송 제한"));
    }

    @Test
    @DisplayName("60초가 지나면 다시 보낼 수 있고, 새 코드로 덮어써서 오입력 횟수도 0으로 돌아간다")
    void resendAfterCooldown() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String oldCode = savedCode(VerificationPurpose.SIGNUP, EMAIL);
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, wrongCode(oldCode)).andExpect(status().isBadRequest());
        String key = AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL);
        // 테스트에서 60초를 기다릴 수 없어서 발송 시각을 61초 전으로 바꾼다
        redisTemplate.opsForHash().put(key, "sentAt", LocalDateTime.now().minusSeconds(61).toString());

        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        assertThat(redisTemplate.getExpire(key)).isBetween(290L, 300L);
        assertThat(redisTemplate.opsForHash().get(key, "failCount")).isEqualTo("0");
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, savedCode(VerificationPurpose.SIGNUP, EMAIL))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 IP에서 1분에 5번을 넘게 보내면 이메일이 달라도 429로 응답한다")
    void ipRateLimit() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 1; i <= 5; i++) {
            send("user" + i + "@example.com", VerificationPurpose.SIGNUP, ip)
                    .andExpect(status().isOk());
        }

        send("user6@example.com", VerificationPurpose.SIGNUP, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.description").value("IP 요청 제한 초과"));
        // 다른 IP는 영향을 받지 않는다
        send("user6@example.com", VerificationPurpose.SIGNUP, "203.0.113.11")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("메일 발송에 실패하면 502로 응답하고, 저장한 코드를 지워서 바로 다시 요청할 수 있게 한다")
    void mailSendFailure() throws Exception {
        willThrow(new MailSendException("SMTP server down")).given(mailSender).send(any(SimpleMailMessage.class));

        send(EMAIL, VerificationPurpose.SIGNUP)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."));

        assertThat(redisTemplate.hasKey(AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL))).isFalse();
    }

    @Test
    @DisplayName("코드가 맞으면 verificationToken을 발급하고, 인증 코드는 지운다")
    void verifySuccess() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String code = savedCode(VerificationPurpose.SIGNUP, EMAIL);

        String body = verifyCode(EMAIL, VerificationPurpose.SIGNUP, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.verificationToken");

        assertThat(redisTemplate.opsForValue().get(AuthRedisKeys.emailVerified(VerificationPurpose.SIGNUP, EMAIL)))
                .isEqualTo(token);
        assertThat(redisTemplate.hasKey(AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL))).isFalse();
    }

    @Test
    @DisplayName("한 번 성공한 코드는 다시 쓸 수 없다")
    void codeIsSingleUse() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String code = savedCode(VerificationPurpose.SIGNUP, EMAIL);
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, code).andExpect(status().isOk());

        verifyCode(EMAIL, VerificationPurpose.SIGNUP, code)
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("코드가 틀리면 400으로 응답하고, description에 남은 시도 횟수를 담는다")
    void verifyMismatch() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String wrong = wrongCode(savedCode(VerificationPurpose.SIGNUP, EMAIL));

        verifyCode(EMAIL, VerificationPurpose.SIGNUP, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 코드가 일치하지 않습니다."))
                .andExpect(jsonPath("$.description").value("남은 시도 횟수: 4"));
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, wrong)
                .andExpect(jsonPath("$.description").value("남은 시도 횟수: 3"));
    }

    @Test
    @DisplayName("5번 틀리면 코드가 지워져서, 그다음엔 맞는 코드를 넣어도 410으로 응답한다")
    void expiredAfterFiveMismatches() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String code = savedCode(VerificationPurpose.SIGNUP, EMAIL);
        for (int remaining = 4; remaining >= 1; remaining--) {
            verifyCode(EMAIL, VerificationPurpose.SIGNUP, wrongCode(code))
                    .andExpect(jsonPath("$.description").value("남은 시도 횟수: " + remaining));
        }

        verifyCode(EMAIL, VerificationPurpose.SIGNUP, wrongCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description").value("남은 시도 횟수: 0"));
        assertThat(redisTemplate.hasKey(AuthRedisKeys.emailCode(VerificationPurpose.SIGNUP, EMAIL))).isFalse();

        verifyCode(EMAIL, VerificationPurpose.SIGNUP, code)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("인증 코드가 만료되었습니다. 다시 요청해주세요."))
                .andExpect(jsonPath("$.description").value("5분 경과 또는 오입력 5회 초과"));
    }

    @Test
    @DisplayName("코드를 보낸 적 없는(또는 만료된) 이메일이면 410으로 응답한다")
    void verifyWithoutCode() throws Exception {
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, "123456")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    @DisplayName("SIGNUP으로 받은 코드는 RESET_PASSWORD 검증에 쓸 수 없다")
    void purposeMustMatch() throws Exception {
        send(EMAIL, VerificationPurpose.SIGNUP).andExpect(status().isOk());
        String code = savedCode(VerificationPurpose.SIGNUP, EMAIL);

        verifyCode(EMAIL, VerificationPurpose.RESET_PASSWORD, code)
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("인증 코드가 숫자 6자리가 아니면 400으로 응답한다")
    void invalidCodeFormat() throws Exception {
        verifyCode(EMAIL, VerificationPurpose.SIGNUP, "12345a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 코드는 숫자 6자리입니다."));
    }

    private ResultActions send(String email, VerificationPurpose purpose) throws Exception {
        return send(email, purpose, DEFAULT_IP);
    }

    private ResultActions send(String email, VerificationPurpose purpose, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/email/send")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "purpose": "%s"}
                        """.formatted(email, purpose)));
    }

    private ResultActions verifyCode(String email, VerificationPurpose purpose, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "purpose": "%s", "code": "%s"}
                        """.formatted(email, purpose, code)));
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private String savedCode(VerificationPurpose purpose, String email) {
        Object code = redisTemplate.opsForHash().get(AuthRedisKeys.emailCode(purpose, email), "code");
        return code == null ? null : code.toString();
    }

    // 저장된 코드와 반드시 다른 6자리 코드
    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }
}
