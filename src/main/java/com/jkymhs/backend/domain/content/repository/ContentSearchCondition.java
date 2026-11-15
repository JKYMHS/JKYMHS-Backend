package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.service.FestivalStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 탐색 목록 조회 조건. 값이 null(또는 빈 집합)인 조건은 쓰지 않는다.
 * 서비스가 요청 값을 검증·변환해서 만들고({@code ContentExploreService}), {@link ContentSearchRepository}가 SQL로 바꾼다.
 *
 * @param keyword        이름 또는 주소에 포함 (이미 특수문자를 지운 값)
 * @param sidoRegionId   시·도로 거를 때
 * @param regionId       시·군·구로 거를 때
 * @param statuses       축제 진행 상태 (하나라도 맞으면 된다)
 * @param today          진행 상태·기간 비교 기준일 (KST)
 * @param overlapFrom    축제 기간이 [overlapFrom, overlapTo]와 겹쳐야 한다 (주말 칩)
 * @param minPopularity  인기 점수 하한 (popular800 칩)
 * @param openDay        지금 영업 중 칩: 이 요일이 쉬는 날이 아니고
 * @param openAt         이 시각에 영업 중
 * @param lclsCodes      분류 코드 (lcls1·lcls2·lcls3 중 하나라도 들어 있으면 된다)
 * @param night          밤 칩 (20시 이후 행사·영업)
 * @param bbox           지도 범위
 */
public record ContentSearchCondition(
        ContentType contentType,
        String keyword,
        Long sidoRegionId,
        Long regionId,
        Long categoryId,
        Set<FestivalStatus> statuses,
        LocalDate today,
        LocalDate overlapFrom,
        LocalDate overlapTo,
        Integer minPopularity,
        DayOfWeek openDay,
        LocalTime openAt,
        Set<String> lclsCodes,
        boolean night,
        Bbox bbox
) {

    /** 지도 범위 (남서쪽 · 북동쪽 꼭짓점). 날짜 변경선을 넘는 범위는 국내 서비스라 다루지 않는다 */
    public record Bbox(double swLat, double swLng, double neLat, double neLng) {
    }
}
