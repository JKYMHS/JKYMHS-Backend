package com.jkymhs.backend.domain.calendar.client.google;

/**
 * 리프레시 토큰이 더는 쓸 수 없다 (구글 토큰 API가 invalid_grant를 줬다).
 * 사용자가 구글 계정 설정에서 앱 권한을 끊었거나, 오래 안 써서 만료됐거나, 비밀번호를 바꾼 경우다. 다시 연동해야 한다.
 */
public class GoogleAuthExpiredException extends RuntimeException {

    public GoogleAuthExpiredException(String message) {
        super(message);
    }
}
