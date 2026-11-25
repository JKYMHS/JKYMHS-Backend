package com.jkymhs.backend.domain.behavior.service;

import java.util.Map;

public record ProfileView(Map<String, Double> lcls2Scores, int signalCount, boolean companionSpecific) {
}
