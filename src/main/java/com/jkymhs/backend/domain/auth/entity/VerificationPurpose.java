package com.jkymhs.backend.domain.auth.entity;

/**
 * 이메일 인증 코드를 어디에 쓰는지. Redis 키에 들어가서 회원가입용 코드로 비밀번호를 재설정하는 일을 막는다.
 */
public enum VerificationPurpose {
    SIGNUP,
    RESET_PASSWORD
}
