package com.jkymhs.backend.domain.calendar.controller;

import com.jkymhs.backend.domain.calendar.dto.request.CalendarBulkUploadRequest;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarBulkUploadResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarJobStatusResponse;
import com.jkymhs.backend.domain.calendar.dto.response.CalendarRetryResponse;
import com.jkymhs.backend.domain.calendar.service.CalendarJobService;
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

@RestController
@RequestMapping("/api/calendar/jobs")
@RequiredArgsConstructor
public class CalendarJobController {
    private final CalendarUploadService calendarUploadService;
    private final CalendarJobService calendarJobService;

    @PostMapping
    public ResponseEntity<CalendarBulkUploadResponse> bulkUpload(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody CalendarBulkUploadRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(calendarUploadService.bulkUpload(userId, request));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<CalendarJobStatusResponse> status(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long jobId) {
        return ResponseEntity.ok(calendarJobService.status(userId, jobId));
    }

    @PostMapping("/{jobId}/retry-failed")
    public ResponseEntity<CalendarRetryResponse> retryFailed(@AuthenticationPrincipal Long userId,
                                                             @PathVariable Long jobId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(calendarUploadService.retryFailed(userId, jobId));
    }
}
