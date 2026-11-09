package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.dto.request.CalendarConnectRequest;
import com.jkymhs.backend.domain.calendar.dto.request.CalendarDefaultRequest;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarConnectResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarDefaultResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarDisconnectResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarListResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarStatusResponse;
import com.jkymhs.backend.domain.calendar.service.CalendarLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구글 캘린더 연동 (마이페이지 > 캘린더).
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarLinkService calendarLinkService;

    @PostMapping
    public ResponseEntity<CalendarConnectResponse> connect(@AuthenticationPrincipal Long userId,
                                                           @Valid @RequestBody CalendarConnectRequest request) {
        return ResponseEntity.ok(calendarLinkService.connect(userId, request));
    }

    @GetMapping
    public ResponseEntity<CalendarStatusResponse> status(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(calendarLinkService.status(userId));
    }

    @GetMapping("/calendars")
    public ResponseEntity<CalendarListResponse> calendars(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(calendarLinkService.calendars(userId));
    }

    @PutMapping("/default")
    public ResponseEntity<CalendarDefaultResponse> changeDefault(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody CalendarDefaultRequest request) {
        return ResponseEntity.ok(calendarLinkService.changeDefault(userId, request.calendarId()));
    }

    // 202: "모두 지우기"는 삭제 작업이 끝난 뒤에 연동이 지워진다. 진행은 jobId로 본다
    @DeleteMapping
    public ResponseEntity<CalendarDisconnectResponse> disconnect(@AuthenticationPrincipal Long userId,
                                                                 @RequestParam(defaultValue = "false") boolean deleteEvents) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(calendarLinkService.disconnect(userId, deleteEvents));
    }
}
