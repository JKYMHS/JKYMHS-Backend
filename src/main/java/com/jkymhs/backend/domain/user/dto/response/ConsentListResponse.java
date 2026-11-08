package com.jkymhs.backend.domain.user.dto.response;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import com.jkymhs.backend.domain.user.service.UserConsentService.ConsentState;

import java.time.LocalDateTime;
import java.util.List;

public record ConsentListResponse(List<Item> consents) {

    public static ConsentListResponse from(List<ConsentState> states) {
        return new ConsentListResponse(states.stream().map(Item::from).toList());
    }

    // agreedAt: 동의한 시각. 동의하지 않았으면 null
    public record Item(ConsentType type, boolean required, boolean agreed, LocalDateTime agreedAt) {

        static Item from(ConsentState state) {
            return new Item(state.type(), state.required(), state.agreed(), state.agreedAt());
        }
    }
}
