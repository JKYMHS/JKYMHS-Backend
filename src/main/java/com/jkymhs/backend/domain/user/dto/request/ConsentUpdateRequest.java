package com.jkymhs.backend.domain.user.dto.request;

import com.jkymhs.backend.domain.user.entity.ConsentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConsentUpdateRequest(
        @NotNull(message = "변경할 동의 항목을 입력해주세요.")
        @Size(min = 1, max = 6, message = "변경할 동의 항목을 입력해주세요.")
        List<@NotNull(message = "변경할 동의 항목을 입력해주세요.") @Valid Item> consents
) {

    @AssertTrue(message = "같은 동의 항목을 두 번 보낼 수 없습니다.")
    public boolean isConsentTypesDistinct() {
        if (consents == null) {
            return true;
        }
        List<ConsentType> types = consents.stream().filter(Objects::nonNull).map(Item::type).filter(Objects::nonNull).toList();
        return types.stream().distinct().count() == types.size();
    }

    public Map<ConsentType, Boolean> toChanges() {
        Map<ConsentType, Boolean> changes = new EnumMap<>(ConsentType.class);
        consents.forEach(item -> changes.put(item.type(), item.agreed()));
        return changes;
    }

    public record Item(
            @NotNull(message = "동의 항목을 입력해주세요.")
            ConsentType type,

            @NotNull(message = "동의 여부를 입력해주세요.")
            Boolean agreed
    ) {
    }
}
