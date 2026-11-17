package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.dto.request.CalendarUploadRequest;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarPreviewResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarUploadResponse;
import com.jkymhs.backend.domain.calendar.service.CalendarUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 업로드 화면 (/trips/:tripId/calendar). 주소는 여행 아래지만 캘린더 도메인이 맡는다.
 */
@RestController
@RequestMapping("/api/trips/{tripId}")
@RequiredArgsConstructor
public class TripCalendarController {

    private final CalendarUploadService calendarUploadService;

    @GetMapping("/calendar-preview")
    public ResponseEntity<CalendarPreviewResponse> preview(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long tripId) {
        return ResponseEntity.ok(calendarUploadService.preview(userId, tripId));
    }

    // 본문 없이 보내면 캘린더·항목 모두 기본값
    @PostMapping("/calendar-uploads")
    public ResponseEntity<CalendarUploadResponse> upload(@AuthenticationPrincipal Long userId, @PathVariable Long tripId,
                                                         @Valid @RequestBody(required = false) CalendarUploadRequest request) {
        CalendarUploadRequest body = request == null ? new CalendarUploadRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(calendarUploadService.upload(userId, tripId, body));
    }
}
