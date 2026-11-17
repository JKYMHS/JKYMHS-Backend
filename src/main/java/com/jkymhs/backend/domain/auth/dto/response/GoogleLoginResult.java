package com.jkymhs.backend.domain.auth.dto.response;

public enum GoogleLoginResult {
    // 구글 계정이 연결된 회원 → 로그인
    LOGGED_IN,
    // 처음 보는 구글 계정 → 비밀번호 없는 계정을 만들고 로그인
    SIGNED_UP,
    // 같은 이메일의 이메일 가입 계정이 있음 → 비밀번호 확인 후 연결해야 함 (토큰 없음)
    LINK_REQUIRED
}
