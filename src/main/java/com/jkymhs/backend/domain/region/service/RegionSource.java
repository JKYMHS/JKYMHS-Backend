package com.jkymhs.backend.domain.region.service;

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
