package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;

import java.util.regex.Pattern;

public final class SearchKeyword {

    static final String TOO_SHORT_MESSAGE = "검색어는 2자 이상 입력해주세요.";
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 50;

    private SearchKeyword() {
    }

    public static String optional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return required(raw);
    }

    public static String required(String raw) {
        String cleaned = raw == null ? "" : SPACES.matcher(NOT_ALLOWED.matcher(raw).replaceAll("")).replaceAll(" ").trim();
        if (cleaned.codePointCount(0, cleaned.length()) < MIN_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT, TOO_SHORT_MESSAGE, "정리한 검색어가 2자 미만");
        }
        return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH).trim();
    }
}
