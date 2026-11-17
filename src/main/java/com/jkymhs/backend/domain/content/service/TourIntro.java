package com.jkymhs.backend.domain.content.service;

/**
 * 운영 정보 원문 (TourAPI detailIntro2 또는 content_details.csv). 값은 모두 원문 그대로이고 비어 있을 수 있다.
 * 시각·요일로 바꾸는 일은 {@link ContentService#applyIntro}가 한다.
 *
 * @param useTime        운영 시간 (관광지 usetime, 문화시설 usetimeculture)
 * @param restDate       쉬는 날 (restdate, restdateculture)
 * @param playTime       축제 공연 시간 (playtime)
 * @param eventStartTime 축제 시작 시각 (eventstarttime, 있을 때만)
 */
public record TourIntro(
        String useTime,
        String restDate,
        String playTime,
        String eventStartTime,
        String eventPlace,
        String fee,
        String ageLimit,
        String host,
        String contact,
        String program
) {
}
