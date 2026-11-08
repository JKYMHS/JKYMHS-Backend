package com.jkymhs.backend.domain.region.service;

/**
 * 적재할 지역 한 줄 (regions.csv 또는 TourAPI ldongCode2).
 *
 * @param ldongSignguCd 시·도이면 빈 문자열
 */
public record RegionSource(String ldongRegnCd, String ldongSignguCd, String name) {

    public RegionSource {
        ldongSignguCd = ldongSignguCd == null ? "" : ldongSignguCd.trim();
        ldongRegnCd = ldongRegnCd.trim();
        name = name.trim();
    }

    public boolean isSido() {
        return ldongSignguCd.isEmpty();
    }
}
