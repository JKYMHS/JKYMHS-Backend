package com.jkymhs.backend.domain.region.service;

import com.jkymhs.backend.domain.region.entity.Region;
import com.jkymhs.backend.domain.region.entity.RegionNames;
import com.jkymhs.backend.domain.region.repository.RegionRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지역 조회·검증. 다른 도메인(탐색, AI 추천, 여행)은 regions 테이블을 직접 읽지 않고 이 서비스만 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionService {

    static final String INVALID_FILTER_MESSAGE = "잘못된 필터 값입니다.";

    private static final String UPSERT_SQL = """
            INSERT INTO regions (parent_id, ldong_regn_cd, ldong_signgu_cd, name, short_name, depth, sort_order)
            VALUES (?, ?, ?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE parent_id = new.parent_id, name = new.name, short_name = new.short_name,
                depth = new.depth, sort_order = new.sort_order
            """;

    private final RegionRepository regionRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @throws CustomException 400 INVALID_INPUT "잘못된 필터 값입니다." — 없는 지역 (FE가 보낸 필터 값이 틀린 경우라 404가 아니다)
     */
    public RegionRef getRegion(Long id) {
        if (id == null) {
            throw invalidFilter("지역 id 없음");
        }
        List<Region> found = regionRepository.findAllWithParentByIdIn(List.of(id));
        if (found.isEmpty()) {
            throw invalidFilter("존재하지 않는 지역: " + id);
        }
        return RegionRef.from(found.getFirst());
    }

    /**
     * 없는 id는 결과 맵에서 빠진다 (검증이 필요하면 {@link #getRegion}).
     */
    public Map<Long, RegionRef> getRegions(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return regionRepository.findAllWithParentByIdIn(new HashSet<>(ids)).stream()
                .map(RegionRef::from)
                .collect(Collectors.toMap(RegionRef::id, Function.identity()));
    }

    /** 모든 지역 (시·도 → 시·군·구 순, 각 단계 안에서는 코드 순). */
    public List<RegionRef> getAllRegions() {
        return regionRepository.findAllWithParent().stream().map(RegionRef::from).toList();
    }

    public List<RegionRef> childrenOf(Long sidoId) {
        RegionRef sido = getRegion(sidoId);
        if (!sido.isSido()) {
            throw invalidFilter("시·도가 아닌 지역: " + sidoId);
        }
        return regionRepository.findChildren(sidoId).stream().map(RegionRef::from).toList();
    }

    /**
     * 시·군·구 id들이 모두 그 시·도 아래인지 확인한다 (AI 추천의 regionId + districtIds 등).
     *
     * @throws CustomException 400 INVALID_INPUT "잘못된 필터 값입니다."
     */
    public void validateDistricts(Long sidoId, List<Long> districtIds) {
        RegionRef sido = getRegion(sidoId);
        if (!sido.isSido()) {
            throw invalidFilter("시·도가 아닌 지역: " + sidoId);
        }
        if (districtIds == null || districtIds.isEmpty()) {
            return;
        }
        Map<Long, RegionRef> districts = getRegions(districtIds);
        for (Long districtId : districtIds) {
            RegionRef district = districts.get(districtId);
            if (district == null || !sidoId.equals(district.parentId())) {
                throw invalidFilter("시·도에 속하지 않는 시·군·구: " + districtId);
            }
        }
    }

    public RegionCodeIndex codeIndex() {
        Map<String, Long> sidoIds = new HashMap<>();
        Map<String, Long> sigunguIds = new HashMap<>();
        for (Region region : regionRepository.findAll()) {
            if (region.isSido()) {
                sidoIds.put(region.getLdongRegnCd(), region.getId());
            } else {
                sigunguIds.put(RegionCodeIndex.sigunguKey(region.getLdongRegnCd(), region.getLdongSignguCd()), region.getId());
            }
        }
        return new RegionCodeIndex(sidoIds, sigunguIds);
    }

    /**
     * 지역 목록을 코드 기준으로 넣거나 이름을 고친다. 같은 코드면 id가 유지된다 (몇 번을 돌려도 결과가 같다).
     * 시·도를 먼저 넣고 그 id를 읽어서 시·군·구의 parent_id로 쓴다.
     * 정렬 순서는 코드 순이다 (세종 36110은 앞 두 자리 36으로 비교해서 충남(44) 앞에 둔다).
     *
     * @return 넣거나 고친 행 수
     */
    @Transactional
    public int upsertAll(List<RegionSource> sources) {
        List<RegionSource> sidos = sources.stream()
                .filter(RegionSource::isSido)
                .sorted(Comparator.comparing((RegionSource s) -> s.ldongRegnCd().substring(0, Math.min(2, s.ldongRegnCd().length())))
                        .thenComparing(RegionSource::ldongRegnCd))
                .toList();
        List<Object[]> sidoRows = new ArrayList<>();
        for (int i = 0; i < sidos.size(); i++) {
            RegionSource sido = sidos.get(i);
            sidoRows.add(new Object[]{null, sido.ldongRegnCd(), "", sido.name(),
                    RegionNames.sidoShortName(sido.name()), Region.DEPTH_SIDO, i + 1});
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, sidoRows);

        Map<String, Long> sidoIds = new HashMap<>();
        jdbcTemplate.query("SELECT id, ldong_regn_cd FROM regions WHERE depth = 1",
                rs -> {
                    sidoIds.put(rs.getString("ldong_regn_cd"), rs.getLong("id"));
                });

        Map<String, List<RegionSource>> byParent = new LinkedHashMap<>();
        sources.stream().filter(s -> !s.isSido())
                .sorted(Comparator.comparing(RegionSource::ldongRegnCd).thenComparing(RegionSource::ldongSignguCd))
                .forEach(s -> byParent.computeIfAbsent(s.ldongRegnCd(), k -> new ArrayList<>()).add(s));
        List<Object[]> sigunguRows = new ArrayList<>();
        for (Map.Entry<String, List<RegionSource>> entry : byParent.entrySet()) {
            Long parentId = sidoIds.get(entry.getKey());
            if (parentId == null) {
                // 시·도 행이 없는 시·군·구는 어디에도 매달 수 없어서 넣지 않는다
                continue;
            }
            List<RegionSource> children = entry.getValue();
            for (int i = 0; i < children.size(); i++) {
                RegionSource child = children.get(i);
                sigunguRows.add(new Object[]{parentId, child.ldongRegnCd(), child.ldongSignguCd(), child.name(),
                        child.name(), Region.DEPTH_SIGUNGU, i + 1});
            }
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, sigunguRows);
        return sidoRows.size() + sigunguRows.size();
    }

    /** 이 id들 중 시·도인 것. 나머지는 시·군·구이거나 없는 id다. */
    public Set<Long> sidoIdsOf(Collection<Long> ids) {
        return getRegions(ids).values().stream().filter(RegionRef::isSido).map(RegionRef::id)
                .collect(Collectors.toSet());
    }

    private static CustomException invalidFilter(String description) {
        return new CustomException(ErrorCode.INVALID_INPUT, INVALID_FILTER_MESSAGE, description);
    }
}
