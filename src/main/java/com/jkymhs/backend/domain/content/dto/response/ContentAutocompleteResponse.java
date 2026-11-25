package com.jkymhs.backend.domain.content.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jkymhs.backend.domain.content.service.FestivalStatus;
import com.jkymhs.backend.domain.content.service.OpenStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ContentAutocompleteResponse(List<Place> places, List<Festival> festivals, List<Region> regions) {

    public record Place(Long id, String name, String regionName, String districtName, String category,
                        String thumbnailUrl, OpenStatus openStatus,
                        @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {
    }

    public record Festival(Long id, String name, String regionName, String districtName, String thumbnailUrl,
                           LocalDate startDate, LocalDate endDate, FestivalStatus status) {
    }

    public record Region(Long regionId, String name, String parentName) {
    }
}
