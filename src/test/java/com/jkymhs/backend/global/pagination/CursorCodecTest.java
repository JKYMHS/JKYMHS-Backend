package com.jkymhs.backend.global.pagination;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    private final CursorCodec cursorCodec = new CursorCodec();

    @Test
    @DisplayName("커서를 만들었다가 풀면 같은 값이 나온다")
    void roundTrip() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("sort", "latest");
        values.put("date", "2026-11-10");
        values.put("score", 812.5);
        values.put("id", 9_000_000_000L);

        Map<String, Object> decoded = cursorCodec.decode(cursorCodec.encode(values));

        assertThat(CursorCodec.requireString(decoded, "sort")).isEqualTo("latest");
        assertThat(CursorCodec.requireString(decoded, "date")).isEqualTo("2026-11-10");
        assertThat(CursorCodec.requireDouble(decoded, "score")).isEqualTo(812.5);
        assertThat(CursorCodec.requireLong(decoded, "id")).isEqualTo(9_000_000_000L);
    }

    @Test
    @DisplayName("커서는 URL에 그대로 넣을 수 있는 문자만 쓴다")
    void urlSafe() {
        String cursor = cursorCodec.encode(Map.of("q", "부산 해운대??//++", "id", 1));

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-base64!!", "%%%"})
    @DisplayName("비어 있거나 base64url이 아닌 커서는 INVALID_CURSOR다")
    void garbage(String cursor) {
        assertInvalidCursor(() -> cursorCodec.decode(cursor));
    }

    @Test
    @DisplayName("base64url이지만 JSON 객체가 아니면 INVALID_CURSOR다")
    void notJsonObject() {
        assertInvalidCursor(() -> cursorCodec.decode(base64Url("hello")));
        assertInvalidCursor(() -> cursorCodec.decode(base64Url("[1,2,3]")));
        assertInvalidCursor(() -> cursorCodec.decode(base64Url("null")));
    }

    @Test
    @DisplayName("1024자보다 긴 커서는 풀어보지 않고 INVALID_CURSOR다")
    void tooLong() {
        assertInvalidCursor(() -> cursorCodec.decode("a".repeat(1025)));
    }

    @Test
    @DisplayName("커서 값의 타입이 틀리거나 없으면 500이 아니라 INVALID_CURSOR다")
    void wrongType() {
        Map<String, Object> decoded = cursorCodec.decode(cursorCodec.encode(Map.of("id", "abc", "sort", 1)));

        assertInvalidCursor(() -> CursorCodec.requireLong(decoded, "id"));
        assertInvalidCursor(() -> CursorCodec.requireDouble(decoded, "missing"));
        assertInvalidCursor(() -> CursorCodec.requireString(decoded, "sort"));
    }

    @Test
    @DisplayName("다른 정렬·필터로 만든 커서는 INVALID_CURSOR이고, 숫자는 타입이 달라도 값이 같으면 통과한다")
    void requireSame() {
        Map<String, Object> decoded = cursorCodec.decode(cursorCodec.encode(Map.of("sort", "latest", "regionId", 11)));

        CursorCodec.requireSame(decoded, "sort", "latest");
        CursorCodec.requireSame(decoded, "regionId", 11L);
        CursorCodec.requireSame(decoded, "categoryId", null);
        assertInvalidCursor(() -> CursorCodec.requireSame(decoded, "sort", "popular"));
        assertInvalidCursor(() -> CursorCodec.requireSame(decoded, "regionId", 26L));
        assertInvalidCursor(() -> CursorCodec.requireSame(decoded, "regionId", null));
    }

    private static String base64Url(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalidCursor(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
