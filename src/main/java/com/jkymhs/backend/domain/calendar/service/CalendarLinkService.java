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

// 구글 호출 중 DB 커넥션을 잡지 않으려고 트랜잭션 없음
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarLinkService {
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

    public CalendarConnectResponse connect(Long userId, CalendarConnectRequest request) {
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

    public CalendarListResponse calendars(Long userId) {
        List<CalendarListResponse.Calendar> calendars = writableCalendars(userId).stream()
                .map(entry -> new CalendarListResponse.Calendar(entry.id(), entry.name(), entry.primary(), true))
                .toList();
        return new CalendarListResponse(calendars);
    }

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

    private String decryptQuietly(String encrypted) {
        try {
            return tokenCipher.decrypt(encrypted);
        } catch (CustomException e) {
            log.warn("캘린더 토큰을 복호화하지 못해 폐기 없이 해제한다");
            return null;
        }
    }
}
