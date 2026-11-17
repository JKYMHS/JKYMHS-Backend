package com.jkymhs.backend.domain.content.client;

/**
 * 오늘 TourAPI 호출 한도를 다 썼다. 우리가 센 호출 수가 {@code tour-api.daily-call-limit}에 닿았거나,
 * 공공데이터포털이 한도 초과(LIMITED_NUMBER_OF_SERVICE_REQUESTS, XML 오류 응답)를 돌려준 경우다.
 * 배치는 이 예외를 받으면 더 부르지 않고 멈춘 뒤 다음 실행에서 이어간다.
 */
public class TourApiQuotaExceededException extends TourApiException {

    public TourApiQuotaExceededException(String message) {
        super(message);
    }
}
