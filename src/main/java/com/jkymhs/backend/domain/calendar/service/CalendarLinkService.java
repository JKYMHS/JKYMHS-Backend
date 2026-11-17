package com.jkymhs.backend.domain.calendar.service;

import com.jkymhs.backend.domain.calendar.client.google.GoogleApiException;
import com.jkymhs.backend.domain.calendar.client.google.GoogleAuthExpiredException;
import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarClient;
import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarEntry;
import com.jkymhs.backend.domain.calendar.client.google.GoogleTokens;
import com.jkymhs.backend.domain.calendar.crypto.CalendarTokenCipher;
import com.jkymhs.backend.domain.calendar.dto.request.CalendarConnectRequest;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarConnectResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarDefaultResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarDisconnectResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarListResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarStatusResponse;
import com.jkymhs.backend.domain.calendar.entity.GoogleCalendarLink;
import com.jkymhs.backend.domain.calendar.repository.GoogleCalendarLinkRepository;
import com.jkymhs.backend.domain.trip.service.TripCalendarSupport;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 캘린더 연동·상태·캘린더 목록·기본 캘린더·해제.
 *
 * 클래스 단위 @Transactional을 두지 않는다. 구글을 부르는 메서드(연동·목록·기본 캘린더·해제)는 DB 트랜잭션(커넥션)을 쥔 채
 * 외부 응답을 기다리지 않게, 구글 호출은 트랜잭션 밖에서 하고 DB 쓰기만 {@link TransactionTemplate}으로 짧게 묶는다.
 * 특히 "토큰 만료 → REVOKED로 바꾸고 401"은 예외를 던져도 REVOKED가 커밋돼야 해서 바깥 트랜잭션이 있으면 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarLinkService {

    // FE가 동의 화면을 열 때 요청하는 캘린더 권한. 사용자가 체크박스를 끄면 scope에서 빠진 채로 돌아온다
    static final Set<String> REQUIRED_SCOPES = Set.of(
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/calendar.calendarlist.readonly");
    private static final String LINK_NOT_FOUND = "연동된 캘린더가 없습니다.";

    private final GoogleCalendarClient googleCalendarClient;
    private final CalendarTokenCipher tokenCipher;
    private final GoogleCalendarLinkRepository linkRepository;
    private final TripCalendarSupport tripCalendarSupport;
    private final CalendarJobService jobService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // ---------- 연동 ----------

    /**
     * 인가 코드 → 토큰 → 권한 확인 → 이메일 → 저장. 이미 연동돼 있으면(만료 포함) 새 토큰으로 바꾸고 ACTIVE로 되돌린다.
     *
     * 리프레시 토큰은 동의 화면을 access_type=offline, prompt=consent로 열어야 온다. 없으면 서버가 나중에 일정을 쓸 수 없어서
     * 연동을 거부한다 (400 CALENDAR_PERMISSION_DENIED — FE가 "권한을 모두 허용해주세요"를 보여준다).
     */
    public CalendarConnectResponse connect(Long userId, CalendarConnectRequest request) {
        // 암호화 키가 없으면 구글 인가 코드를 쓰기(일회용) 전에 실패시킨다
        tokenCipher.checkKey();
        GoogleTokens tokens = googleCalendarClient.exchangeCode(request.code(), request.redirectUri());
        Set<String> granted = scopes(tokens.scope());
        if (!granted.containsAll(REQUIRED_SCOPES)) {
            throw new CustomException(ErrorCode.CALENDAR_PERMISSION_DENIED, "캘린더 권한 누락: " + tokens.scope());
        }
        if (!StringUtils.hasText(tokens.refreshToken())) {
            throw new CustomException(ErrorCode.CALENDAR_PERMISSION_DENIED, "refresh_token 없음 (offline 동의 아님)");
        }
        String email = googleCalendarClient.fetchEmail(tokens.accessToken());
        String encrypted = tokenCipher.encrypt(tokens.refreshToken());
        String grantedScopes = String.join(" ", granted.stream().sorted().toList());

        GoogleCalendarLink link = transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            Optional<GoogleCalendarLink> existing = linkRepository.findByUserIdForUpdate(userId);
            if (existing.isPresent()) {
                existing.get().relink(email, encrypted, grantedScopes, now);
                return existing.get();
            }
            return linkRepository.save(GoogleCalendarLink.create(userId, email, encrypted, grantedScopes, now));
        });
        return new CalendarConnectResponse(true, link.getGoogleEmail(), link.getDefaultCalendarId());
    }

    private static Set<String> scopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet());
    }

    // ---------- 상태 ----------

    @Transactional(readOnly = true)
    public CalendarStatusResponse status(Long userId) {
        Optional<GoogleCalendarLink> link = linkRepository.findByUserId(userId);
        long uploaded = tripCalendarSupport.uploadedItems(userId).size();
        List<CalendarStatusResponse.Trip> trips = tripCalendarSupport.syncedTrips(userId).stream()
                .map(view -> new CalendarStatusResponse.Trip(view.tripId(), view.title(), view.sync()))
                .toList();
        return link.map(found -> new CalendarStatusResponse(true, found.getStatus(), found.getGoogleEmail(),
                        found.getLinkedAt(), found.getDefaultCalendarId(), uploaded, trips))
                .orElseGet(() -> new CalendarStatusResponse(false, null, null, null, null, uploaded, trips));
    }

    // ---------- 캘린더 목록·기본 캘린더 ----------

    /** 일정을 쓸 수 있는 캘린더만 (owner·writer). 읽기 전용(공휴일·구독 캘린더)은 업로드 대상이 될 수 없어서 뺀다 */
    public CalendarListResponse calendars(Long userId) {
        List<CalendarListResponse.Calendar> calendars = writableCalendars(userId).stream()
                .map(entry -> new CalendarListResponse.Calendar(entry.id(), entry.name(), entry.primary(), true))
                .toList();
        return new CalendarListResponse(calendars);
    }

    /**
     * 기본 캘린더 바꾸기. 목록에 있고 쓸 수 있는 캘린더만 고를 수 있다. "primary"는 구글이 늘 받아주는 기본 캘린더 별칭이라 확인 없이 받는다.
     */
    public CalendarDefaultResponse changeDefault(Long userId, String calendarId) {
        String target = calendarId.trim();
        if (!GoogleCalendarLink.PRIMARY_CALENDAR.equals(target)) {
            boolean writable = writableCalendars(userId).stream().anyMatch(entry -> entry.id().equals(target));
            if (!writable) {
                throw new CustomException(ErrorCode.CALENDAR_NOT_WRITABLE, "쓸 수 없거나 없는 캘린더: " + target);
            }
        } else {
            activeLink(userId);
        }
        transactionTemplate.executeWithoutResult(status -> linkRepository.findByUserIdForUpdate(userId)
                .ifPresent(link -> link.changeDefaultCalendar(target)));
        return new CalendarDefaultResponse(target);
    }

    private List<GoogleCalendarEntry> writableCalendars(Long userId) {
        GoogleCalendarLink link = activeLink(userId);
        String accessToken = accessToken(link);
        return callGoogle(() -> googleCalendarClient.calendarList(accessToken), userId).stream()
                .filter(GoogleCalendarEntry::writable).toList();
    }

    private GoogleCalendarLink activeLink(Long userId) {
        return linkRepository.findByUserId(userId).filter(GoogleCalendarLink::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_LINKED, "연동 없음 또는 만료"));
    }

    /** 리프레시 토큰으로 액세스 토큰을 받는다. 토큰이 거부되면(invalid_grant) 연동을 REVOKED로 바꾸고 401 */
    private String accessToken(GoogleCalendarLink link) {
        String refreshToken = tokenCipher.decrypt(link.getEncryptedRefreshToken());
        return callGoogle(() -> googleCalendarClient.refreshAccessToken(refreshToken), link.getUserId());
    }

    private <T> T callGoogle(Supplier<T> call, Long userId) {
        try {
            return call.get();
        } catch (GoogleAuthExpiredException e) {
            markRevoked(userId);
            throw new CustomException(ErrorCode.CALENDAR_AUTH_EXPIRED, "리프레시 토큰 거부(invalid_grant)");
        } catch (GoogleApiException e) {
            if (e.status() == 401) {
                markRevoked(userId);
                throw new CustomException(ErrorCode.CALENDAR_AUTH_EXPIRED, "구글 API 401");
            }
            throw new CustomException(ErrorCode.GOOGLE_SERVER_ERROR, e.getMessage());
        }
    }

    private void markRevoked(Long userId) {
        transactionTemplate.executeWithoutResult(status -> linkRepository.findByUserIdForUpdate(userId)
                .ifPresent(GoogleCalendarLink::revoke));
    }

    // ---------- 해제 ----------

    /**
     * deleteEvents=true: 올린 일정을 모두 지우는 DELETE_ALL 작업을 넣는다. 연동 행은 작업이 끝날 때 지운다
     * (작업이 토큰 사본을 들고 있어서 그사이 다시 연동해도 새 연동을 건드리지 않는다).
     * deleteEvents=false 또는 이미 만료된 연동: 바로 연동 행을 지우고 항목의 캘린더 정보를 비운다. 구글 쪽 일정은 그대로 남는다.
     * 토큰 폐기(revoke)는 커밋 뒤에 부르고, 실패해도 해제는 그대로 둔다.
     */
    public CalendarDisconnectResponse disconnect(Long userId, boolean deleteEvents) {
        GoogleCalendarLink link = linkRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, LINK_NOT_FOUND, "연동 없음"));
        if (deleteEvents && link.isActive()) {
            Long jobId = transactionTemplate.execute(status -> {
                GoogleCalendarLink locked = linkRepository.findByUserIdForUpdate(userId).orElseThrow(
                        () -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, LINK_NOT_FOUND, "연동 없음"));
                List<CalendarJobOp> ops = tripCalendarSupport.uploadedItems(userId).stream()
                        .map(CalendarJobOp::delete).toList();
                return jobService.createDeleteAll(userId, locked.getEncryptedRefreshToken(), ops).getId();
            });
            return new CalendarDisconnectResponse(jobId);
        }

        String refreshToken = decryptQuietly(link.getEncryptedRefreshToken());
        transactionTemplate.executeWithoutResult(status -> {
            linkRepository.deleteByUserIdInBulk(userId);
            tripCalendarSupport.clearAll(userId);
        });
        if (refreshToken != null) {
            googleCalendarClient.revoke(refreshToken);
        }
        return new CalendarDisconnectResponse(null);
    }

    // 키를 바꿨거나 잃어버려도 연동 해제는 돼야 한다 (토큰 폐기만 건너뛴다)
    private String decryptQuietly(String encrypted) {
        try {
            return tokenCipher.decrypt(encrypted);
        } catch (CustomException e) {
            log.warn("캘린더 토큰을 복호화하지 못해 폐기 없이 해제한다");
            return null;
        }
    }
}
