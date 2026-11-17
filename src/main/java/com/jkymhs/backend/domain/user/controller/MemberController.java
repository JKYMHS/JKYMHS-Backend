package com.jkymhs.backend.domain.user.controller;

import com.jkymhs.backend.domain.auth.service.TokenService.IssuedTokens;
import com.jkymhs.backend.domain.user.dto.request.ConsentUpdateRequest;
import com.jkymhs.backend.domain.user.dto.request.GoogleAccountLinkRequest;
import com.jkymhs.backend.domain.user.dto.request.NicknameUpdateRequest;
import com.jkymhs.backend.domain.user.dto.request.PasswordChangeRequest;
import com.jkymhs.backend.domain.user.dto.request.PasswordSetRequest;
import com.jkymhs.backend.domain.user.dto.request.PreferenceSaveRequest;
import com.jkymhs.backend.domain.user.dto.request.WithdrawRequest;
import com.jkymhs.backend.domain.user.dto.response.ConsentListResponse;
import com.jkymhs.backend.domain.user.dto.response.GoogleAccountLinkResponse;
import com.jkymhs.backend.domain.user.dto.response.MemberMeResponse;
import com.jkymhs.backend.domain.user.dto.response.OnboardingStatusResponse;
import com.jkymhs.backend.domain.user.dto.response.PasswordChangeResponse;
import com.jkymhs.backend.domain.user.dto.response.PreferenceResponse;
import com.jkymhs.backend.domain.user.dto.response.WithdrawSummaryResponse;
import com.jkymhs.backend.domain.user.service.MemberService;
import com.jkymhs.backend.domain.user.service.PreferenceService;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.domain.user.service.WithdrawSummaryService;
import com.jkymhs.backend.global.security.RefreshTokenCookieProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 API. 모두 로그인이 필요해서(SecurityConfig의 anyRequest().authenticated()) userId는 항상 있다.
 */
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final PreferenceService preferenceService;
    private final UserConsentService userConsentService;
    private final WithdrawSummaryService withdrawSummaryService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @GetMapping
    public ResponseEntity<MemberMeResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(memberService.getMe(userId));
    }

    @PatchMapping
    public ResponseEntity<MemberMeResponse> changeNickname(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ResponseEntity.ok(memberService.changeNickname(userId, request.nickname()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<PreferenceResponse> savePreferences(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PreferenceSaveRequest request
    ) {
        return ResponseEntity.ok(PreferenceResponse.from(preferenceService.save(userId, request.styleIds(),
                request.pace(), request.timeOfDay(), request.accessible(), request.pet())));
    }

    @GetMapping("/preferences")
    public ResponseEntity<PreferenceResponse> getPreferences(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(PreferenceResponse.from(preferenceService.get(userId)));
    }

    @PostMapping("/onboarding/skip")
    public ResponseEntity<OnboardingStatusResponse> skipOnboarding(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(new OnboardingStatusResponse(preferenceService.skipOnboarding(userId)));
    }

    @GetMapping("/consents")
    public ResponseEntity<ConsentListResponse> getConsents(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ConsentListResponse.from(userConsentService.getConsents(userId)));
    }

    @PutMapping("/consents")
    public ResponseEntity<ConsentListResponse> updateConsents(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConsentUpdateRequest request
    ) {
        return ResponseEntity.ok(ConsentListResponse.from(userConsentService.updateConsents(userId, request.toChanges())));
    }

    @PostMapping("/google")
    public ResponseEntity<GoogleAccountLinkResponse> linkGoogle(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody GoogleAccountLinkRequest request
    ) {
        return ResponseEntity.ok(memberService.linkGoogle(userId, request.code(), request.redirectUri()));
    }

    @DeleteMapping("/google")
    public ResponseEntity<Void> unlinkGoogle(@AuthenticationPrincipal Long userId) {
        memberService.unlinkGoogle(userId);
        return ResponseEntity.noContent().build();
    }

    // 모든 기기의 로그인이 끊기므로 이 기기에 새 액세스 토큰(본문)과 리프레시 토큰(쿠키)을 준다
    @PatchMapping("/password")
    public ResponseEntity<PasswordChangeResponse> changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordChangeRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        IssuedTokens tokens = memberService.changePassword(userId, request.currentPassword(), request.newPassword(),
                userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(tokens.refreshToken()).toString())
                .body(new PasswordChangeResponse(tokens.accessToken()));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> setPassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PasswordSetRequest request
    ) {
        memberService.setPassword(userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/withdraw-summary")
    public ResponseEntity<WithdrawSummaryResponse> getWithdrawSummary(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(WithdrawSummaryResponse.from(withdrawSummaryService.summarize(userId)));
    }

    // DELETE에 본문을 싣는다. 본문이 없어도(required = false) 본인 확인 실패 401로 응답한다
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) WithdrawRequest request
    ) {
        memberService.withdraw(userId, request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.expire().toString())
                .build();
    }
}
