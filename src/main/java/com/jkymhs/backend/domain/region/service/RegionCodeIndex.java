package com.jkymhs.backend.domain.region.service;

import java.util.Map;

/**
 * 법정동 코드 → 지역 id 변환표. 콘텐츠 적재·동기화가 콘텐츠 수만 건의 코드를 id로 바꿀 때
 * 한 건마다 DB를 조회하지 않도록 한 번 읽어서 메모리에 둔다.
 */
public final class RegionCodeIndex {

    private final Map<String, Long> sidoIds;
    private final Map<String, Long> sigunguIds;

    RegionCodeIndex(Map<String, Long> sidoIds, Map<String, Long> sigunguIds) {
        this.sidoIds = sidoIds;
        this.sigunguIds = sigunguIds;
    }

    static String sigunguKey(String regnCd, String signguCd) {
        return regnCd + ":" + signguCd;
    }

    /**
     * 시·군·구가 없으면(세종처럼 시·군·구 코드가 시·도 코드와 같게 오는 경우 포함) region은 시·도로 채운다.
     * 탐색에서 "세종" 하나로 거를 수 있고, 시·군·구 필터에서도 빠지지 않게 하려는 것이다.
     *
     * @return 시·도 코드부터 모르면 null
     */
    public Resolved resolve(String regnCd, String signguCd) {
        if (regnCd == null || regnCd.isBlank()) {
            return null;
        }
        Long sidoId = sidoIds.get(regnCd.trim());
        if (sidoId == null) {
            return null;
        }
        Long sigunguId = signguCd == null ? null : sigunguIds.get(sigunguKey(regnCd.trim(), signguCd.trim()));
        return new Resolved(sidoId, sigunguId != null ? sigunguId : sidoId);
    }

    public record Resolved(Long sidoRegionId, Long regionId) {
    }
}
