package com.jkymhs.backend.domain.content.service;

import java.util.List;

public record TourDetail(
        String overview,
        String homepage,
        TourIntro intro,
        List<String> imageUrls,
        Boolean petAllowed
) {
}
