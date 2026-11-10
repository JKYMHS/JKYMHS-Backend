package com.jkymhs.backend.domain.route;

import com.jkymhs.backend.domain.route.service.LatLng;
import com.jkymhs.backend.domain.route.service.PolylineEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolylineEncoderTest {

    @Test
    @DisplayName("구글 문서의 예시 좌표를 같은 문자열로 인코딩한다")
    void googleExample() {
        String encoded = PolylineEncoder.encode(List.of(new LatLng(38.5, -120.2), new LatLng(40.7, -120.95),
                new LatLng(43.252, -126.453)));

        assertThat(encoded).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }
}
