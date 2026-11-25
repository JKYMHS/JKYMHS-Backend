package com.jkymhs.backend.domain.region.service;

import java.util.Map;

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
