package com.jkymhs.backend.domain.trip.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TripProperties.class, VisitCheckProperties.class, AppProperties.class})
public class TripConfig {
}
