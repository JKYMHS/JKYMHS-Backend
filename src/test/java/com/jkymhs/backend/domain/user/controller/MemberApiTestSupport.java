package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.support.IntegrationTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.EnumSet;

/**
 * 마이페이지 API 테스트의 공통 준비물: 이메일·구글 회원 만들기, 액세스 토큰 붙이기.
 * 스프링 설정을 바꾸지 않아서(빈 추가·교체 없음) 상속해도 테스트 컨텍스트를 새로 띄우지 않는다.
 */
abstract class MemberApiTestSupport extends IntegrationTestSupport {

    protected static final String EMAIL = "minji@example.com";
    protected static final String PASSWORD = "abc12345";
    protected static final String GOOGLE_SUB = "10769150350006150715";
    protected static final String GOOGLE_EMAIL = "minji@gmail.com";

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TokenService tokenService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected UserConsentService userConsentService;

    // 이메일로 가입한 회원 (가입 API처럼 필수 3개 + 행동 데이터에 동의, 나머지는 미동의로 저장)
    protected User saveEmailUser() {
        User user = userRepository.save(User.createWithPassword(EMAIL, passwordEncoder.encode(PASSWORD)));
        userConsentService.saveInitialConsents(user,
                EnumSet.of(ConsentType.AGE_14, ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.BEHAVIOR_DATA));
        return user;
    }

    // 구글로만 가입한 회원 (비밀번호 없음, 동의 행 없음 — 구글 가입 API는 동의를 받지 않는다)
    protected User saveGoogleOnlyUser() {
        return userRepository.save(User.createWithGoogle(GOOGLE_EMAIL, GOOGLE_SUB, GOOGLE_EMAIL));
    }

    protected String accessToken(User user) {
        return tokenService.issue(user, "test").accessToken();
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String accessToken) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String accessToken,
                                                        String body) {
        return auth(request, accessToken).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
