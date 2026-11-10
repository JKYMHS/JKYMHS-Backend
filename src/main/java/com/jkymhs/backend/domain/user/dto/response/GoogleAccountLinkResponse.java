package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.dto.response.MemberMeResponse.Google;

// 내 정보(MemberMeResponse)의 google과 같은 모양이라 FE가 그 부분만 바꿔 끼우면 된다
public record GoogleAccountLinkResponse(Google google) {
}
