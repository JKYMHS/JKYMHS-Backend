package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.content.entity.BatchJobLog;
import com.jkymhs.backend.domain.content.entity.BatchJobName;
import com.jkymhs.backend.domain.content.entity.BatchJobStatus;
import com.jkymhs.backend.domain.content.service.BatchJobLogService;
import com.jkymhs.backend.domain.trip.entity.Trip;
import com.jkymhs.backend.domain.trip.repository.TripRepository;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.service.UserConsentService;
import com.jkymhs.backend.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitCheckMailJob {
    private final TripRepository tripRepository;
    private final VisitCheckService visitCheckService;
    private final UserConsentService userConsentService;
    private final UserService userService;
    private final VisitCheckMailSender mailSender;
    private final VisitCheckProperties properties;
    private final BatchJobLogService batchJobLogService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(cron = "${visit-check.mail-cron:0 0 10 * * *}", zone = "Asia/Seoul")
    public void scheduledRun() {
        run();
    }

    public VisitCheckMailResult run() {
        BatchJobLog jobLog = batchJobLogService.start(BatchJobName.VISIT_CHECK_MAIL);
        int sent = 0;
        int failed = 0;
        try {
            LocalDate today = LocalDate.now(clock);
            List<Trip> candidates = tripRepository.findVisitMailCandidates(today,
                    today.minusDays(properties.mailLookbackDays()));
            for (Trip trip : candidates) {
                if (!shouldSend(trip, today)) {
                    continue;
                }
                try {
                    mailSender.send(userService.getById(trip.getUserId()).getEmail(), trip);
                    markSent(trip.getId());
                    sent++;
                } catch (MailException e) {
                    log.warn("방문 확인 메일 발송 실패 tripId={}: {}", trip.getId(), e.getMessage());
                    failed++;
                }
            }
            batchJobLogService.finish(jobLog, failed == 0 ? BatchJobStatus.SUCCESS : BatchJobStatus.PARTIAL,
                    sent, failed, 0, null);
            return new VisitCheckMailResult(sent, failed);
        } catch (RuntimeException e) {
            log.error("방문 확인 메일 배치 실패", e);
            batchJobLogService.finish(jobLog, BatchJobStatus.FAILED, sent, failed, 0, e.getClass().getSimpleName());
            throw e;
        }
    }

    private boolean shouldSend(Trip trip, LocalDate today) {
        Long userId = trip.getUserId();
        return visitCheckService.isEligible(trip, today)
                && userConsentService.isAgreed(userId, ConsentType.BEHAVIOR_DATA)
                && userConsentService.isAgreed(userId, ConsentType.EMAIL_NOTIFICATION);
    }

    private void markSent(Long tripId) {
        transactionTemplate.executeWithoutResult(status -> tripRepository.findById(tripId)
                .ifPresent(trip -> trip.markVisitEmailSent(LocalDateTime.now(clock))));
    }
}
