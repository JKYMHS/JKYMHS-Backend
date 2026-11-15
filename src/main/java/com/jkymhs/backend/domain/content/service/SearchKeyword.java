package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * 검색어 정리. 글자·숫자·공백만 남기고(특수문자 제거), 공백은 하나로 줄인다.
 *
 * 특수문자를 지우는 이유: LIKE의 %·_ 같은 와일드카드가 사용자 입력에서 그대로 쓰이지 않게 하고,
 * "경복궁!!"처럼 붙은 문장부호 때문에 결과가 없어지지 않게 하기 위해서다.
 * 지운 뒤 2자 미만이면 결과가 너무 많고 의미도 없어서 400으로 막는다 (기능 명세서: 2자 이상).
 */
public final class SearchKeyword {

    static final String TOO_SHORT_MESSAGE = "검색어는 2자 이상 입력해주세요.";
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N}\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 50;

    private SearchKeyword() {
    }

    /**
     * 목록 검색(q 선택)용. 비어 있으면 검색하지 않는다는 뜻이라 null을 준다.
     *
     * @throws CustomException 400 INVALID_INPUT "검색어는 2자 이상 입력해주세요."
     */
    public static String optional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return required(raw);
    }

    /**
     * 자동완성(q 필수)용.
     *
     * @throws CustomException 400 INVALID_INPUT "검색어는 2자 이상 입력해주세요."
     */
    public static String required(String raw) {
        String cleaned = raw == null ? "" : SPACES.matcher(NOT_ALLOWED.matcher(raw).replaceAll("")).replaceAll(" ").trim();
        if (cleaned.codePointCount(0, cleaned.length()) < MIN_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT, TOO_SHORT_MESSAGE, "정리한 검색어가 2자 미만");
        }
        // 지나치게 긴 검색어는 잘라서 쓴다 (화면 입력란도 짧다)
        return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH).trim();
    }
}
