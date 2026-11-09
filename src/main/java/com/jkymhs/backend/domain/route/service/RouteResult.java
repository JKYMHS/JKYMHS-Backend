package com.jkymhs.backend.domain.route.service;

/**
 * 구간 하나의 이동 결과. 일정 항목(plan_items)의 이동 컬럼과 AI 추천 코스의 move 항목이 같은 값을 쓴다.
 *
 * @param mode           추천 이동 수단
 * @param minutes        추천 이동 수단의 소요 시간(분)
 * @param altMode        다른 선택지 (대중교통 ↔ 택시). 걸어가는 구간이거나 한쪽 결과만 있으면 null
 * @param altMinutes     다른 선택지의 소요 시간(분)
 * @param summary        대중교통 요약 ("지하철 1호선 → 버스 472 · 환승 1회"). 대중교통이 추천일 때만
 * @param polyline       지도에 그릴 선 (encoded polyline, 정밀도 5). 택시는 실제 도로, 나머지는 출발→도착 직선
 * @param estimated      외부 API 없이 직선거리로 어림잡은 값이면 true (화면에 "약"을 붙인다)
 * @param distanceMeters 추천 이동 수단 기준 거리(m)
 */
public record RouteResult(
        MoveMode mode,
        int minutes,
        MoveMode altMode,
        Integer altMinutes,
        String summary,
        String polyline,
        boolean estimated,
        int distanceMeters
) {
}
