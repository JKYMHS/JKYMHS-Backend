package com.jkymhs.backend.domain.wish;

import com.jkymhs.backend.domain.auth.service.TokenService;
import com.jkymhs.backend.domain.content.ContentFixtures;
import com.jkymhs.backend.domain.content.service.ContentService;
import com.jkymhs.backend.domain.content.service.ContentSource;
import com.jkymhs.backend.domain.content.service.TourClassificationService;
import com.jkymhs.backend.domain.region.service.RegionService;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.support.ClockTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 찜·행동 데이터·인기 점수 테스트의 공통 준비물: 지역·분류 기본값, 콘텐츠 넣기, 회원과 액세스 토큰.
 * 시계를 쓰는 테스트가 많아서 ClockTestSupport를 상속한다 (스프링 컨텍스트를 새로 만들지 않는다).
 */
public abstract class BehaviorTestSupport extends ClockTestSupport {

    protected static final String PASSWORD = "abc12345";

    @Autowired
    protected ContentService contentService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private RegionService regionService;

    @Autowired
    private TourClassificationService tourClassificationService;

    @Autowired
    private UserConsentService userConsentService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private int userSeq;

    @BeforeEach
    void seedReferenceData() {
        regionService.upsertAll(ContentFixtures.REGIONS);
        tourClassificationService.upsertAll(ContentFixtures.CLASSIFICATIONS);
    }

    protected void saveContents(ContentSource... sources) {
        contentService.upsert(List.of(sources), contentService.loadContext());
    }

    /** 이메일 가입 회원 (필수 3개 동의 + 행동 데이터는 인자대로) */
    protected User saveUser(boolean behaviorConsent) {
        userSeq++;
        User user = userRepository.save(
                User.createWithPassword("user" + userSeq + "@example.com", passwordEncoder.encode(PASSWORD)));
        Set<ConsentType> agreed = EnumSet.of(ConsentType.AGE_14, ConsentType.TERMS, ConsentType.PRIVACY);
        if (behaviorConsent) {
            agreed.add(ConsentType.BEHAVIOR_DATA);
        }
        userConsentService.saveInitialConsents(user, agreed);
        return user;
    }

    protected String token(User user) {
        return tokenService.issue(user, "test").accessToken();
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected int wishCountOf(long contentId) {
        return jdbcTemplate.queryForObject("SELECT wish_count FROM contents WHERE id = ?", Integer.class, contentId);
    }

    protected int popularityOf(long contentId) {
        return jdbcTemplate.queryForObject("SELECT popularity_score FROM contents WHERE id = ?", Integer.class, contentId);
    }
}
