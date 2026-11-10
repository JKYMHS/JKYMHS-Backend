package com.jkymhs.backend.domain.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.jkymhs.backend.domain.auth.entity.VerificationPurpose;
import com.jkymhs.backend.domain.auth.repository.RefreshTokenRepository;
import com.jkymhs.backend.domain.auth.service.AuthRedisKeys;
import com.jkymhs.backend.domain.auth.service.VerificationTokenService;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.entity.UserConsent;
import com.jkymhs.backend.domain.user.repository.UserConsentRepository;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.domain.user.service.UserService;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 메일 API(#3)는 다른 브랜치에서 만들고 있어서, 인증을 마친 상태는 VerificationTokenService.issue로 직접 만든다.
 */
class SignupApiTest extends IntegrationTestSupport {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "abc12345";
    private static final String PASSWORD_POLICY_MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.";
    private static final String REQUIRED_CONSENT_MESSAGE = "필수 약관에 모두 동의해야 합니다.";
    private static final String CONSENTS = """
            {"age14": true, "terms": true, "privacy": true, "behaviorData": true, "emailNotification": false}""";

    @Autowired
    private VerificationTokenService verificationTokenService;

    // 동시 가입 테스트에서만 existsByEmail 결과를 바꾼다. 나머지 테스트에서는 실제 메서드가 그대로 실행된다
    @MockitoSpyBean
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserConsentRepository userConsentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("가입하면 201과 토큰을 주고, 회원·약관 동의 5개·리프레시 토큰을 저장한다")
    void signup() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        String body = signup(EMAIL, verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.onboardingStatus").value("NOT_STARTED"))
                .andReturn().getResponse().getContentAsString();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(jwtProvider.parseUserId(JsonPath.read(body, "$.accessToken"))).isEqualTo(user.getId());
        // 비밀번호는 원문이 아니라 BCrypt 해시로 저장된다
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2a$");
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();

        Map<ConsentType, UserConsent> consents = userConsentRepository.findAll().stream()
                .collect(Collectors.toMap(UserConsent::getConsentType, Function.identity()));
        assertThat(consents).hasSize(5);
        for (ConsentType type : List.of(ConsentType.AGE_14, ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.BEHAVIOR_DATA)) {
            assertThat(consents.get(type).isAgreed()).isTrue();
            assertThat(consents.get(type).getAgreedAt()).isNotNull();
        }
        assertThat(consents.get(ConsentType.EMAIL_NOTIFICATION).isAgreed()).isFalse();
        assertThat(consents.get(ConsentType.EMAIL_NOTIFICATION).getAgreedAt()).isNull();
        assertThat(consents.values()).allSatisfy(consent -> assertThat(consent.getUpdatedAt()).isNotNull());

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 쓴 인증 토큰으로 다시 가입하면 401")
    void reusedVerificationToken() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);
        signup(EMAIL, verificationToken, PASSWORD, CONSENTS).andExpect(status().isCreated());

        signup(EMAIL, verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 인증이 만료되었습니다. 다시 인증해주세요."));
    }

    @Test
    @DisplayName("인증 토큰이 틀리면 401이고 회원은 만들어지지 않는다")
    void invalidVerificationToken() throws Exception {
        verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        signup(EMAIL, "wrong-token", PASSWORD, CONSENTS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 인증이 만료되었습니다. 다시 인증해주세요."));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("다른 이메일로 받은 인증 토큰으로는 가입할 수 없다")
    void verificationTokenOfOtherEmail() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, "other@example.com");

        signup(EMAIL, verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} = false")
    @ValueSource(strings = {"age14", "terms", "privacy"})
    @DisplayName("필수 약관에 하나라도 동의하지 않으면 400이고, 인증 토큰은 폐기되지 않는다")
    void requiredConsentNotAgreed(String field) throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);
        String consents = CONSENTS.replace("\"" + field + "\": true", "\"" + field + "\": false");

        signup(EMAIL, verificationToken, PASSWORD, consents)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(REQUIRED_CONSENT_MESSAGE));

        // 형식 검증은 서비스보다 먼저 실행된다. 고쳐서 다시 보내면 같은 토큰으로 가입할 수 있다
        assertThat(redisTemplate.hasKey(AuthRedisKeys.emailVerified(VerificationPurpose.SIGNUP, EMAIL))).isTrue();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("선택 약관도 값을 보내지 않으면 400")
    void optionalConsentMissing() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        signup(EMAIL, verificationToken, PASSWORD, """
                {"age14": true, "terms": true, "privacy": true, "behaviorData": true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("선택 약관 동의 여부를 입력해주세요."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "abc1234",               // 7자
            "abcdefgh",              // 숫자 없음
            "12345678",              // 영문 없음
            "abc123456789012345678", // 21자
            "abc 12345"              // 공백 포함
    })
    @DisplayName("비밀번호 규칙(영문+숫자 포함 8~20자, 공백 불가)을 어기면 400")
    void invalidPassword(String password) throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        signup(EMAIL, verificationToken, password, CONSENTS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PASSWORD_POLICY_MESSAGE));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409")
    void duplicateEmail() throws Exception {
        userService.register(User.createWithPassword(EMAIL, passwordEncoder.encode(PASSWORD)));
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        signup(EMAIL, verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 확인을 통과한 직후 같은 이메일이 먼저 저장돼도(동시 가입) DB UNIQUE 제약에 걸려 409, 약관·토큰은 저장되지 않는다")
    void concurrentDuplicateSignup() throws Exception {
        userService.register(User.createWithPassword(EMAIL, passwordEncoder.encode(PASSWORD)));
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);
        // 다른 요청이 existsByEmail과 저장 사이에 끼어든 상황을 만든다
        doReturn(false).when(userService).existsByEmail(anyString());

        signup(EMAIL, verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userConsentRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("이메일은 공백을 없애고 소문자로 저장해서, 대소문자가 달라도 같은 계정으로 로그인된다")
    void emailIsNormalized() throws Exception {
        String verificationToken = verificationTokenService.issue(VerificationPurpose.SIGNUP, EMAIL);

        signup("User@Example.com ", verificationToken, PASSWORD, CONSENTS)
                .andExpect(status().isCreated());
        assertThat(userRepository.findByEmail(EMAIL)).isPresent();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());
    }

    private ResultActions signup(String email, String verificationToken, String password, String consents) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "JUnit")
                .content("""
                        {"email": "%s", "verificationToken": "%s", "password": "%s", "consents": %s}
                        """.formatted(email, verificationToken, password, consents)));
    }
}
