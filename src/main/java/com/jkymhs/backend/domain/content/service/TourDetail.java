package com.jkymhs.backend.domain.content.service;

import java.util.List;

/**
 * 상세 화면용으로 TourAPI에서 새로 받은 값 (D1 상세 갱신이 detailCommon2·detailIntro2·detailImage2·detailPetTour2를 부른 결과).
 * 일부 호출이 실패하면 그 부분은 null로 넘긴다. null인 부분은 저장된 값을 그대로 둔다.
 *
 * @param intro      null이면 운영 정보를 고치지 않는다
 * @param imageUrls  null이면 사진을 고치지 않는다. 빈 목록이면 사진을 모두 지운다
 * @param petAllowed null이면 고치지 않는다
 */
public record TourDetail(
        String overview,
        String homepage,
        TourIntro intro,
        List<String> imageUrls,
        Boolean petAllowed
) {
}
