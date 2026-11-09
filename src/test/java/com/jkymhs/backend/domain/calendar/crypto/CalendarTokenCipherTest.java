package com.jkymhs.backend.domain.calendar.crypto;

import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarProperties;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarTokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    @Test
    @DisplayName("암호화한 값은 평문을 포함하지 않고, 같은 키로 되돌릴 수 있다. IV가 매번 달라서 같은 평문도 결과가 다르다")
    void roundTrip() {
        CalendarTokenCipher cipher = cipher(KEY);

        String first = cipher.encrypt("1//refresh-token");
        String second = cipher.encrypt("1//refresh-token");

        assertThat(first).doesNotContain("refresh-token").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("1//refresh-token");
        assertThat(cipher.decrypt(second)).isEqualTo("1//refresh-token");
        // IV 12바이트 + 평문 16바이트 + 태그 16바이트
        assertThat(Base64.getDecoder().decode(first)).hasSize(12 + 16 + 16);
    }

    @Test
    @DisplayName("값을 한 글자라도 바꾸거나 다른 키로 풀면 503 (GCM 인증 태그 검사)")
    void tamperedOrWrongKey() {
        CalendarTokenCipher cipher = cipher(KEY);
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("token"));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> cipher(OTHER_KEY).decrypt(cipher.encrypt("token")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> cipher.decrypt("not-base64!")).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("키가 없거나 32바이트가 아니면 쓸 때 503 SERVICE_UNAVAILABLE (서버 시작은 막지 않는다)")
    void invalidKey() {
        for (String key : new String[]{null, "", "c2hvcnQ=", "not base64"}) {
            CalendarTokenCipher cipher = cipher(key);
            assertThatThrownBy(cipher::checkKey).isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private static CalendarTokenCipher cipher(String key) {
        return new CalendarTokenCipher(new GoogleCalendarProperties("id", "secret", "t", "r", "u", "a", key,
                Duration.ofSeconds(2), 50, 3, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMinutes(5),
                new GoogleCalendarProperties.Executor(2, 4, 100)));
    }
}
