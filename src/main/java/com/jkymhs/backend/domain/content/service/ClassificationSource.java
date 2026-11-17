package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.IoType;

/**
 * 적재할 분류 코드 한 줄 (lcls_codes.csv 또는 TourAPI lclsSystmCode2).
 */
public record ClassificationSource(String code, int depth, String parentCode, String name, IoType ioType) {
}
