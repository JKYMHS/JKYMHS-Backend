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
