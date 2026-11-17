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

/**
 * 커서 기반 목록의 cursor 문자열을 만들고 푼다. 형식: 마지막 항목의 정렬 값을 담은 JSON 객체 → base64url(패딩 없음).
 * 예: {@code {"sort":"latest","date":"2026-11-10","id":123}} → {@code eyJzb3J0Ijoi...}
 *
 * FE에는 "불투명한 문자열"이라고만 약속한다. 내용을 FE가 해석하지 않으므로 나중에 담는 값을 바꿔도 FE를 고칠 필요가 없다.
 * 페이지 번호(offset) 대신 커서를 쓰는 이유: 목록을 넘기는 사이에 항목이 추가·삭제돼도 중복·누락이 없고,
 * 뒤 페이지로 갈수록 느려지는 OFFSET 스캔이 없다 ({@code WHERE (정렬값, id) > (커서값)} 조건으로 인덱스를 탄다).
 *
 * 다른 정렬·필터에서 만든 커서를 받으면 이상한 위치부터 보여주게 되므로, 만들 때 정렬·필터 값을 같이 넣고
 * 풀 때 {@link #requireSame}으로 비교해서 다르면 400 INVALID_CURSOR를 준다.
 *
 * JSON 숫자는 크기에 따라 Integer/Long으로 풀리므로 값은 {@link #requireLong} 같은 메서드로 꺼낸다.
 * 사용자가 커서를 조작해서 타입이 틀려도 ClassCastException(500) 대신 400이 나게 하기 위해서다.
 */
@Component
public class CursorCodec {

    // 정상 커서는 수십~수백 자다. 지나치게 긴 값은 풀어보지 않고 거절한다
    private static final int MAX_CURSOR_LENGTH = 1024;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String encode(Map<String, Object> values) {
        byte[] json = jsonMapper.writeValueAsBytes(values);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    }

    /**
     * @throws CustomException INVALID_CURSOR — 비어 있음, base64url이 아님, JSON 객체가 아님, 너무 김
     */
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

    /**
     * 커서를 만들 때의 정렬·필터 값과 지금 요청의 값이 같은지 확인한다. 숫자 1과 1L처럼 타입만 다른 것은 같다고 본다.
     */
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
