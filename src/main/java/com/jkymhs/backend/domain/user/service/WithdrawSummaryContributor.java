package com.jkymhs.backend.domain.user.service;

import java.util.Map;

public interface WithdrawSummaryContributor {

    void contribute(Long userId, Map<String, Long> counts);
}
