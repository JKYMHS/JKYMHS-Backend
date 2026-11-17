package com.jkymhs.backend.domain.content.service;

/**
 * 운영 정보(detailIntro2)를 새로 받을 콘텐츠. detailIntro2는 contentTypeId를 같이 보내야 해서 둘을 묶는다.
 */
public record IntroTarget(Long contentId, int tourContentTypeId) {
}
