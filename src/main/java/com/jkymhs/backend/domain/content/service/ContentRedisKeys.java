package com.jkymhs.backend.domain.content.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 콘텐츠 관련 Redis 키. 키 문자열은 여기서만 만든다 (다른 곳에서 문자열을 직접 쓰면 오타 하나로 다른 키를 보게 된다).
 */
public final class ContentRedisKeys {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private ContentRedisKeys() {
    }

    /** String(정수). 인기 점수 배치(D3)가 돌 때마다 1 올린다. 인기순 커서에 넣어 배치 전후 커서를 구분한다. TTL 없음, 없으면 0으로 읽는다. */
    public static String popularityVersion() {
        return "popularity:version";
    }

    /** AI 추천 후보 캐시(D5) 전체. 동기화 배치가 콘텐츠를 바꾸면 SCAN으로 찾아 지운다. */
    public static String candidatesPattern() {
        return "candidates:*";
    }

    /** String(정수, INCR). 그날 TourAPI 호출 수. 일일 한도를 넘지 않으려고 센다. TTL 2일 (다음 날 확인용으로 하루 더 둔다). */
    public static String tourApiCalls(LocalDate date) {
        return "tourapi:calls:" + date.format(YYYYMMDD);
    }

    /** String. 상세 갱신(D1) 중복 실행 방지 락. TTL 10초. */
    public static String detailLock(Long contentId) {
        return "lock:detail:" + contentId;
    }
}
