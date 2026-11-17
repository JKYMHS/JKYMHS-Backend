package com.jkymhs.backend.domain.content.service;

import java.time.LocalDate;

/** 축제 기간 (시작일·종료일 포함) */
public record EventPeriod(LocalDate start, LocalDate end) {
}
