package com.jkymhs.backend.domain.content.service;

import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchKeywordTest {

    @Test
    @DisplayName("특수문자는 지우고 공백은 하나로 줄인다 (LIKE 와일드카드 %, _도 지워진다)")
    void normalize() {
        assertThat(SearchKeyword.required("  경복궁!! ")).isEqualTo("경복궁");
        assertThat(SearchKeyword.required("서울   중구")).isEqualTo("서울 중구");
        assertThat(SearchKeyword.required("50%_할인")).isEqualTo("50할인");
        assertThat(SearchKeyword.required("N서울타워")).isEqualTo("N서울타워");
    }

    @Test
    @DisplayName("선택 검색어가 비어 있으면 null이고, 지운 뒤 2자 미만이면 400이다")
    void tooShort() {
        assertThat(SearchKeyword.optional(null)).isNull();
        assertThat(SearchKeyword.optional("   ")).isNull();
        assertThatThrownBy(() -> SearchKeyword.optional("경!"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> {
                    assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(e.getMessage()).isEqualTo("검색어는 2자 이상 입력해주세요.");
                });
        assertThatThrownBy(() -> SearchKeyword.required(null)).isInstanceOf(CustomException.class);
    }
}
