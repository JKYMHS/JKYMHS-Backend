package com.jkymhs.backend.domain.content.service;

import java.time.LocalTime;

/**
 * 운영 시간 원문에서 뽑은 값. 못 읽은 값은 null이다.
 *
 * @param close 밤을 넘기는 곳(18:00~02:00)은 open보다 이르다
 */
public record OperatingHours(LocalTime open, LocalTime close, LocalTime lastEntry) {

    public static final OperatingHours UNKNOWN = new OperatingHours(null, null, null);
}
