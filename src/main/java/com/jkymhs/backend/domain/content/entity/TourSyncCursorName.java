package com.jkymhs.backend.domain.content.entity;

/**
 * MODIFIED_SINCE: 다음 동기화가 "이 날짜 이후에 바뀐 콘텐츠"를 받을 기준일 (yyyy-MM-dd).
 * INTRO_LAST_ID: 마지막으로 운영 시간(detailIntro2)을 받은 콘텐츠 id. 대상은 오래된 순으로 고르므로 이어받기에 꼭 필요하지는 않고, 진행 확인용이다.
 */
public enum TourSyncCursorName {
    MODIFIED_SINCE,
    INTRO_LAST_ID
}
