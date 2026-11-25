package com.jkymhs.backend.global.pagination;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

@Component
public class CursorCodec {

    private static final int MAX_CURSOR_LENGTH = 1024;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String encode(Map<String, Object> values) {
        byte[] json = jsonMapper.writeValueAsBytes(values);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    public Map<String, Object> decode(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH) {
            throw invalid("커서가 비어 있거나 너무 김");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            Map<String, Object> values = jsonMapper.readValue(new String(json, StandardCharsets.UTF_8), MAP_TYPE);
            if (values == null) {
                throw invalid("커서 내용이 없음");
            }
            return values;
        } catch (IllegalArgumentException | JacksonException e) {
            throw invalid("커서 형식 오류");
        }
    }

    public static long requireLong(Map<String, Object> cursor, String key) {
        if (cursor.get(key) instanceof Number number) {
            return number.longValue();
        }
        throw invalid("커서에 숫자 " + key + " 없음");
    }

    public static double requireDouble(Map<String, Object> cursor, String key) {
        if (cursor.get(key) instanceof Number number) {
            return number.doubleValue();
        }
        throw invalid("커서에 숫자 " + key + " 없음");
    }

    public static String requireString(Map<String, Object> cursor, String key) {
        if (cursor.get(key) instanceof String value) {
            return value;
        }
        throw invalid("커서에 문자열 " + key + " 없음");
    }

    public static void requireSame(Map<String, Object> cursor, String key, Object expected) {
        Object actual = cursor.get(key);
        boolean same = (actual instanceof Number a && expected instanceof Number b)
                ? a.toString().equals(b.toString())
                : Objects.equals(actual, expected);
        if (!same) {
            throw invalid("다른 정렬·필터의 커서: " + key);
        }
    }

    private static CustomException invalid(String description) {
        return new CustomException(ErrorCode.INVALID_CURSOR, description);
    }
}
