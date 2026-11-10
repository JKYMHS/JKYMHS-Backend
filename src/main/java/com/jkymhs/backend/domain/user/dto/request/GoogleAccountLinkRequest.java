package com.jkymhs.backend.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 마이페이지에서 구글 계정 연결. FE가 구글 동의 화면에서 받은 인가 코드와, 그때 쓴 redirect_uri를 보낸다.
 * (로그인 중 연결은 auth의 GoogleLinkRequest로 linkToken + 비밀번호를 받는다. 이름이 겹치지 않게 다르게 지었다)
 */
public record GoogleAccountLinkRequest(
        @NotBlank(message = "구글 인가 코드를 입력해주세요.")
        String code,

        @NotBlank(message = "redirectUri를 입력해주세요.")
        String redirectUri
) {
}
