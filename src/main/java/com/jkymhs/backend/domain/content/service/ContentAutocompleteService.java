package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.content.dto.response.ContentAutocompleteResponse;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository.AutocompleteRow;
import com.jkymhs.backend.domain.region.service.RegionRef;
import com.jkymhs.backend.domain.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 검색창 자동완성 (최대 10개: 지역 2개 + 장소·축제를 순위대로 섞어서).
 *
 * 장소와 축제를 각각 "남은 칸 수"만큼 순위대로 읽고, 두 목록을 같은 기준(지역 → 앞부분 일치 → 인기 점수)으로 합쳐서 남은 칸을 채운다.
 * 한 종류가 칸을 다 차지하지 않고, 더 잘 맞는 결과가 먼저 보이게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentAutocompleteService {

    private static final int MAX_TOTAL = 10;
    private static final int MAX_REGIONS = 2;

    private static final Comparator<Candidate> RANK_ORDER = Comparator
            .comparingInt((Candidate c) -> c.row().rank()).reversed()
            .thenComparing(Comparator.comparingInt((Candidate c) -> c.row().popularity()).reversed())
            .thenComparingLong(c -> c.row().id());

    private final ContentSearchRepository contentSearchRepository;
    private final ContentService contentService;
    private final RegionService regionService;
    private final ExploreProperties exploreProperties;
    private final Clock clock;

    /**
     * @param tab null이면 장소·축제 모두
     * @throws com.jkymhs.backend.global.exception.CustomException 400 "검색어는 2자 이상 입력해주세요.", 400 "잘못된 필터 값입니다."(없는 지역)
     */
    public ContentAutocompleteResponse autocomplete(String q, Long regionId, CategoryTab tab) {
        String keyword = SearchKeyword.required(q);
        RegionRef region = regionId == null ? null : regionService.getRegion(regionId);
        Long sidoId = region != null && region.isSido() ? region.id() : null;
        Long districtId = region != null && !region.isSido() ? region.id() : null;
        LocalDateTime now = LocalDateTime.now(clock);

        List<ContentAutocompleteResponse.Region> regions = matchRegions(keyword);
        int remaining = MAX_TOTAL - regions.size();

        List<Candidate> candidates = new ArrayList<>();
        if (tab == null || tab == CategoryTab.PLACE) {
            contentSearchRepository.autocomplete(ContentType.ATTRACTION, keyword, sidoId, districtId, null, remaining)
                    .forEach(row -> candidates.add(new Candidate(row, false)));
        }
        if (tab == null || tab == CategoryTab.FESTIVAL) {
            // 끝난 축제는 갈 수 없어서 자동완성에 넣지 않는다
            contentSearchRepository.autocomplete(ContentType.FESTIVAL, keyword, sidoId, districtId, now.toLocalDate(),
                            remaining)
                    .forEach(row -> candidates.add(new Candidate(row, true)));
        }
        List<Candidate> chosen = candidates.stream().sorted(RANK_ORDER).limit(remaining).toList();
        Map<Long, ContentSnapshot> snapshots = contentService.findSnapshots(chosen.stream().map(c -> c.row().id()).toList());

        List<ContentAutocompleteResponse.Place> places = new ArrayList<>();
        List<ContentAutocompleteResponse.Festival> festivals = new ArrayList<>();
        for (Candidate candidate : chosen) {
            ContentSnapshot s = snapshots.get(candidate.row().id());
            if (s == null) {
                continue;
            }
            if (candidate.festival()) {
                festivals.add(new ContentAutocompleteResponse.Festival(s.id(), s.name(), s.regionName(), s.districtName(),
                        s.thumbnailUrl(), s.eventStartDate(), s.eventEndDate(),
                        FestivalStatus.of(s.eventStartDate(), s.eventEndDate(), now.toLocalDate())));
            } else {
                places.add(new ContentAutocompleteResponse.Place(s.id(), s.name(), s.regionName(), s.districtName(),
                        s.categoryName(), s.thumbnailUrl(),
                        OpenStatusCalculator.of(s, now, exploreProperties.closingSoonMinutes()), s.closeTime()));
            }
        }
        return new ContentAutocompleteResponse(places, festivals, regions);
    }

    // 지역은 280여 개라 메모리에서 거른다. "중구"처럼 이름이 같은 시·군·구가 여러 개면 코드 순으로 앞의 것이 먼저다
    private List<ContentAutocompleteResponse.Region> matchRegions(String keyword) {
        return regionService.getAllRegions().stream()
                .filter(r -> r.name().startsWith(keyword) || r.fullName().startsWith(keyword))
                .limit(MAX_REGIONS)
                .map(r -> new ContentAutocompleteResponse.Region(r.id(), r.name(), r.parentName()))
                .toList();
    }

    private record Candidate(AutocompleteRow row, boolean festival) {
    }
}
