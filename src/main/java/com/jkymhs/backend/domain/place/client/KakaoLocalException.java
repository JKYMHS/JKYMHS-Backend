package com.jkymhs.backend.domain.place.client;

/**
 * 카카오 로컬 API 호출 실패 (키 없음, 4xx·5xx, 타임아웃, 응답 형식 오류).
 * 메시지에 요청 주소를 넣지 않는다: 좌표→주소 변환은 주소에 사용자 위치(x, y)가 들어 있어서 로그에 남기면 안 된다.
 */
public class KakaoLocalException extends RuntimeException {

    public KakaoLocalException(String message) {
        super(message);
    }
}
