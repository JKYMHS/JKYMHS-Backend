package com.jkymhs.backend.domain.route.dto.request;

import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.Segment;
import com.jkymhs.backend.global.model.Companion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RouteCalculateRequest(
        @NotEmpty(message = "구간을 1개 이상 보내주세요.")
        @Size(max = 30, message = "구간은 최대 30개까지 계산할 수 있습니다.")
        List<@NotNull(message = "구간을 입력해주세요.") @Valid SegmentRequest> segments,
        Companion companion
) {
    public List<Segment> toSegments() {
        return segments.stream().map(s -> new Segment(s.from().toLatLng(), s.to().toLatLng())).toList();
    }

    public record SegmentRequest(
            @NotNull(message = "출발 좌표를 입력해주세요.") @Valid Point from,
            @NotNull(message = "도착 좌표를 입력해주세요.") @Valid Point to
    ) {
    }

    public record Point(
            @NotNull(message = "위도를 입력해주세요.")
            @DecimalMin(value = "-90", message = "위도가 올바르지 않습니다.")
            @DecimalMax(value = "90", message = "위도가 올바르지 않습니다.") Double lat,
            @NotNull(message = "경도를 입력해주세요.")
            @DecimalMin(value = "-180", message = "경도가 올바르지 않습니다.")
            @DecimalMax(value = "180", message = "경도가 올바르지 않습니다.") Double lng
    ) {
        LatLng toLatLng() {
            return new LatLng(lat, lng);
        }
    }
}
