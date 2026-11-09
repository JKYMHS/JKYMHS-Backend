package com.jkymhs.backend.domain.trip.entity;

import com.jkymhs.backend.global.web.CodeEnum;

/**
 * 여행 방문 확인 상태. 아직 대상이 아닌 여행은 trips.visit_check_status가 null이다.
 * 끝난 여행이 처음 방문 확인 대상이 되는 순간(배너 조회·방문 확인 조회) PENDING이 된다.
 */
public enum VisitCheckStatus implements CodeEnum {
    PENDING,    // 확인 전이거나 일부만 확인함
    DISMISSED,  // "나중에"를 두 번 눌러서 더 묻지 않음
    DONE;       // 모든 장소를 확인함

    @Override
    public String code() {
        return name();
    }
}
