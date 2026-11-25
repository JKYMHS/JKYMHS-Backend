package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.dto.request.ContentNearbyRequest;
import com.jkymhs.backend.domain.content.dto.response.ContentNearbyResponse;
import com.jkymhs.backend.domain.content.dto.response.NearbyPicksResponse;
import com.jkymhs.backend.domain.content.entity.ContentType;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository;
import com.jkymhs.backend.domain.content.repository.ContentSearchRepository.DistanceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentNearbyService {

    static final int PICKS_RADIUS_METERS = 1000;
    static final int PICKS_LIMIT = 4;
    static final double WALK_METERS_PER_MINUTE = 67d;
    static final int MAX_RADIUS_METERS = 5000;
    static final int DEFAULT_NEARBY_SIZE = 5;

    private static final List<ContentType> PICK_TYPES = List.of(ContentType.ATTRACTION, ContentType.FESTIVAL);

    private final ContentSearchRepository contentSearchRepository;
    private final ContentService contentService;
    private final Clock clock;

    public NearbyPicksResponse nearbyPicks(Long contentId) {
        ContentSnapshot origin = contentService.getActiveSnapshot(contentId);
        List<DistanceRow> rows = contentSearchRepository.findWithin(PICK_TYPES, origin.lat(), origin.lng(),
                PICKS_RADIUS_METERS, LocalDate.now(clock), origin.id(), false, PICKS_LIMIT);
        Map<Long, ContentSnapshot> snapshots = snapshotsOf(rows);
        List<NearbyPicksResponse.Item> items = new ArrayList<>();
        for (DistanceRow row : rows) {
            ContentSnapshot s = snapshots.get(row.id());
            if (s != null) {
                int meters = (int) Math.round(row.distanceMeters());
                items.add(new NearbyPicksResponse.Item(s.id(), s.contentType(), s.name(), s.categoryName(),
                        s.thumbnailUrl(), s.lat(), s.lng(), meters, (int) Math.ceil(meters / WALK_METERS_PER_MINUTE)));
            }
        }
        return new NearbyPicksResponse(items);
    }

    public ContentNearbyResponse nearby(ContentNearbyRequest request) {
        List<ContentType> types = List.of(request.type().contentType());
        int radius = request.radius() == null ? request.type().defaultRadiusMeters() : request.radius();
        int size = request.size() == null ? DEFAULT_NEARBY_SIZE : request.size();
        double lat = request.lat();
        double lng = request.lng();

        int total = contentSearchRepository.countWithin(types, lat, lng, radius);
        if (total == 0 && radius < MAX_RADIUS_METERS) {
            radius = Math.min(radius * 2, MAX_RADIUS_METERS);
            total = contentSearchRepository.countWithin(types, lat, lng, radius);
        }
        List<DistanceRow> rows = total == 0 ? List.of()
                : contentSearchRepository.findWithin(types, lat, lng, radius, null, null, true, size);
        Map<Long, ContentSnapshot> snapshots = snapshotsOf(rows);
        List<ContentNearbyResponse.Item> items = new ArrayList<>();
        for (DistanceRow row : rows) {
            ContentSnapshot s = snapshots.get(row.id());
            if (s != null) {
                items.add(new ContentNearbyResponse.Item(s.id(), s.contentType(), s.name(), s.address(),
                        s.categoryName(), s.thumbnailUrl(), s.lat(), s.lng(), (int) Math.round(row.distanceMeters())));
            }
        }
        return new ContentNearbyResponse(items, total, radius);
    }

    public int countWithin(ContentType type, double lat, double lng, int radiusMeters) {
        return contentSearchRepository.countWithin(List.of(type), lat, lng, radiusMeters);
    }

    private Map<Long, ContentSnapshot> snapshotsOf(List<DistanceRow> rows) {
        return contentService.findSnapshots(rows.stream().map(DistanceRow::id).toList());
    }
}
