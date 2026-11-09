package com.jkymhs.backend.domain.place.service;

import com.jkymhs.backend.domain.place.client.KakaoAddress;
import com.jkymhs.backend.domain.place.client.KakaoLocalClient;
import com.jkymhs.backend.domain.place.client.KakaoLocalException;
import com.jkymhs.backend.domain.place.client.KakaoPlace;
import com.jkymhs.backend.domain.place.dto.response.PlaceSearchResponse;
import com.jkymhs.backend.domain.place.dto.response.ReverseGeocodeResponse;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 추천·새 여행의 출발지 검색. DB를 쓰지 않아서 트랜잭션이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    static final int MAX_RESULTS = 10;
    static final String SEARCH_FAILED_MESSAGE = "주소 검색에 실패했습니다. 다시 시도해주세요.";
    static final String REVERSE_FAILED_MESSAGE = "주소를 찾지 못했습니다. 다시 시도해주세요.";

    private final KakaoLocalClient kakaoLocalClient;

    /**
     * 키워드 검색(장소 이름) 결과를 먼저, 그 뒤에 주소 검색 결과를 붙인다.
     * "용산역"은 키워드 검색에, "한강대로 405"는 주소 검색에만 나와서 둘 다 부른다. 같은 주소가 이미 있으면 주소 결과는 뺀다.
     *
     * @throws CustomException 502 EXTERNAL_API_ERROR "주소 검색에 실패했습니다. 다시 시도해주세요."
     */
    public PlaceSearchResponse search(String query) {
        List<KakaoPlace> keyword;
        List<KakaoPlace> address;
        try {
            keyword = kakaoLocalClient.searchKeyword(query, MAX_RESULTS);
            address = kakaoLocalClient.searchAddress(query);
        } catch (KakaoLocalException e) {
            log.warn("Kakao local search failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, SEARCH_FAILED_MESSAGE, e.getMessage());
        }
        List<PlaceSearchResponse.Item> items = new ArrayList<>();
        Set<String> seenAddresses = new HashSet<>();
        for (KakaoPlace place : keyword) {
            if (items.size() == MAX_RESULTS) {
                break;
            }
            items.add(toItem(place));
            remember(seenAddresses, place);
        }
        for (KakaoPlace place : address) {
            if (items.size() == MAX_RESULTS) {
                break;
            }
            if (seenAddresses.contains(place.address())
                    || (place.roadAddress() != null && seenAddresses.contains(place.roadAddress()))) {
                continue;
            }
            items.add(toItem(place));
            remember(seenAddresses, place);
        }
        return new PlaceSearchResponse(items);
    }

    /**
     * 현재 위치 → 주소. 좌표는 카카오에 넘기기만 하고 저장·로그하지 않는다 (PRD: 위치 정보 저장 금지).
     *
     * @throws CustomException 502 EXTERNAL_API_ERROR "주소를 찾지 못했습니다. 다시 시도해주세요." (호출 실패 또는 주소 없음)
     */
    public ReverseGeocodeResponse reverseGeocode(double lat, double lng) {
        KakaoAddress found;
        try {
            found = kakaoLocalClient.coordToAddress(lat, lng).orElse(null);
        } catch (KakaoLocalException e) {
            log.warn("Kakao reverse geocode failed: {}", e.getMessage());
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, REVERSE_FAILED_MESSAGE, e.getMessage());
        }
        if (found == null) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, REVERSE_FAILED_MESSAGE, "좌표에 해당하는 주소 없음");
        }
        String name = found.buildingName() != null ? found.buildingName()
                : found.roadAddress() != null ? found.roadAddress() : found.address();
        return new ReverseGeocodeResponse(name, found.address(), found.roadAddress());
    }

    private static PlaceSearchResponse.Item toItem(KakaoPlace place) {
        return new PlaceSearchResponse.Item(place.placeId(), place.name(), place.address(), place.roadAddress(),
                place.category(), place.lat(), place.lng());
    }

    private static void remember(Set<String> seen, KakaoPlace place) {
        if (place.address() != null) {
            seen.add(place.address());
        }
        if (place.roadAddress() != null) {
            seen.add(place.roadAddress());
        }
    }
}
