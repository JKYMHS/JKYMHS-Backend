package com.jkymhs.backend.domain.region.service;

import com.jkymhs.backend.domain.region.entity.Region;

/**
 * 다른 도메인에 넘기는 지역 정보. 엔티티를 그대로 넘기지 않아서 지연 로딩·영속성 컨텍스트에 기대지 않게 한다.
 *
 * @param name     화면용 짧은 이름 ("서울", "중구")
 * @param parentId 시·도이면 null
 * @param fullName 시·군·구는 "서울 중구", 시·도는 "서울"
 */
public record RegionRef(Long id, String name, int depth, Long parentId, String parentName, String fullName) {

    static RegionRef from(Region region) {
        Region parent = region.getParent();
        if (parent == null) {
            return new RegionRef(region.getId(), region.getShortName(), region.getDepth(), null, null, region.getShortName());
        }
        return new RegionRef(region.getId(), region.getShortName(), region.getDepth(), parent.getId(),
                parent.getShortName(), parent.getShortName() + " " + region.getShortName());
    }

    public boolean isSido() {
        return depth == Region.DEPTH_SIDO;
    }
}
