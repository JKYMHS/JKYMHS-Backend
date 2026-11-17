package com.jkymhs.backend.domain.user.dto.request;

/**
 * 탈퇴 본인 확인. 비밀번호가 있는 회원은 password, 구글로만 가입한 회원은 googleCode + redirectUri(구글 재인증)를 보낸다.
 * 어느 값이 필요한지는 회원에 따라 달라서 필드 검증(@NotBlank)을 걸지 않고, 빠지면 서비스에서 401 "본인 확인에 실패했습니다."로 응답한다.
 */
public record WithdrawRequest(
        String password,
        String googleCode,
        String redirectUri
) {

    public static final WithdrawRequest EMPTY = new WithdrawRequest(null, null, null);
}
