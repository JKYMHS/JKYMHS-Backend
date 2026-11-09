package com.jkymhs.backend.domain.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 검색창 자동완성. 화면이 "관광지 / 축제 / 지역" 묶음으로 보여줘서 묶음별로 나눠 준다 (합계 최대 10개).
 */
public record ContentAutocompleteResponse(List<Place> places, List<Festival> festivals, List<Region> regions) {

    public record Place(Long id, String name, String regionName, String districtName, String category,
                        String thumbnailUrl, OpenStatus openStatus,
                        @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {
    }

    public record Festival(Long id, String name, String regionName, String districtName, String thumbnailUrl,
                           LocalDate startDate, LocalDate endDate, FestivalStatus status) {
    }

    /**
     * @param parentName 시·군·구면 시·도 이름 ("서울"), 시·도면 null
     */
    public record Region(Long regionId, String name, String parentName) {
    }
}
