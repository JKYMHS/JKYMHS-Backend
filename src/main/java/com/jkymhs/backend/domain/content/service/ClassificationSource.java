package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.domain.content.entity.IoType;

public record ClassificationSource(String code, int depth, String parentCode, String name, IoType ioType) {
}
