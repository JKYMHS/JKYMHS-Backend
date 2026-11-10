package com.jkymhs.backend.domain.trip.service;

import com.jkymhs.backend.domain.trip.TripApiTestSupport;
import com.jkymhs.backend.domain.user.entity.ConsentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메일 서버만 가짜(JavaMailSender 목)로 바꾼다. 배치는 @Scheduled를 기다리지 않고 run()을 직접 부른다.
 */
class VisitCheckMailJobTest extends TripApiTestSupport {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private VisitCheckMailJob job;

    @Test
    @DisplayName("행동 데이터·이메일 알림에 모두 동의한 회원의 끝난 여행에만 한 번 보내고, 실행 기록을 남긴다")
    void sendOnceToConsentedUsers() throws Exception {
        long agreed = endedTripOf("agreed@example.com", ConsentType.BEHAVIOR_DATA, ConsentType.EMAIL_NOTIFICATION);
        endedTripOf("nomail@example.com", ConsentType.BEHAVIOR_DATA);
        endedTripOf("nobehavior@example.com", ConsentType.EMAIL_NOTIFICATION);

        VisitCheckMailResult result = job.run();

        assertThat(result).isEqualTo(new VisitCheckMailResult(1, 0));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertThat(mail.getTo()).containsExactly("agreed@example.com");
        assertThat(mail.getSubject()).isEqualTo("[어디 갈래] 지난 여행, 다녀오셨나요?");
        assertThat(mail.getText()).contains("http://localhost:3000/trips/" + agreed + "?visitCheck=1");
        assertThat(jdbcTemplate.queryForObject("SELECT visit_email_sent_at IS NOT NULL FROM trips WHERE id = ?",
                Boolean.class, agreed)).isTrue();
        assertThat(jdbcTemplate.queryForMap("SELECT status, processed_count FROM batch_job_logs WHERE job_name = 'VISIT_CHECK_MAIL'"))
                .containsEntry("status", "SUCCESS").containsEntry("processed_count", 1);

        assertThat(job.run()).isEqualTo(new VisitCheckMailResult(0, 0));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP가 실패하면 PARTIAL로 남기고 보냄 표시를 하지 않아서 다음 실행에서 다시 보낸다")
    void mailFailure() throws Exception {
        long tripId = endedTripOf("agreed@example.com", ConsentType.BEHAVIOR_DATA, ConsentType.EMAIL_NOTIFICATION);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(job.run()).isEqualTo(new VisitCheckMailResult(0, 1));
        assertThat(jdbcTemplate.queryForObject("SELECT visit_email_sent_at FROM trips WHERE id = ?", Object.class, tripId))
                .isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM batch_job_logs", String.class)).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("끝난 지 7일이 넘은 여행과 장소가 없는 확정안은 보내지 않는다")
    void skipOldAndEmpty() throws Exception {
        load(place(101L, "경복궁", 37.5796, 126.9770));
        String token = token(saveUser("agreed@example.com", ConsentType.BEHAVIOR_DATA, ConsentType.EMAIL_NOTIFICATION));
        long[] old = createTrip(token, "오래된 여행", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21));
        savePlan(token, old[0], old[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        createTrip(token, "빈 여행", LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8));

        assertThat(job.run()).isEqualTo(new VisitCheckMailResult(0, 0));
    }

    private long endedTripOf(String email, ConsentType... consents) throws Exception {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contents WHERE id = 101", Long.class) == 0) {
            load(place(101L, "경복궁", 37.5796, 126.9770));
        }
        String token = token(saveUser(email, consents));
        long[] ids = createTrip(token, "지난 여행", LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 8));
        savePlan(token, ids[0], ids[1], 0, items(item(null, 101L, 1, 1))).andExpect(status().isOk());
        return ids[0];
    }
}
