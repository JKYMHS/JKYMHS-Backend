package com.jkymhs.backend.global.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPageTest {

    @Test
    @DisplayName("size + 1개가 조회되면 size개만 담고 마지막 항목으로 다음 커서를 만든다")
    void hasNext() {
        CursorPage<Integer> page = CursorPage.of(List.of(1, 2, 3, 4), 3, last -> "after-" + last);

        assertThat(page.items()).containsExactly(1, 2, 3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("after-3");
    }

    @Test
    @DisplayName("size개 이하가 조회되면 마지막 페이지라 nextCursor는 null이다")
    void lastPage() {
        CursorPage<Integer> full = CursorPage.of(List.of(1, 2, 3), 3, last -> "after-" + last);
        CursorPage<Integer> empty = CursorPage.of(List.of(), 3, last -> "after-" + last);

        assertThat(full.items()).containsExactly(1, 2, 3);
        assertThat(full.hasNext()).isFalse();
        assertThat(full.nextCursor()).isNull();
        assertThat(empty.items()).isEmpty();
        assertThat(empty.hasNext()).isFalse();
        assertThat(empty.nextCursor()).isNull();
    }

    @Test
    @DisplayName("map은 항목만 바꾸고 커서와 hasNext는 그대로 둔다")
    void map() {
        CursorPage<String> page = CursorPage.of(List.of(1, 2, 3), 2, last -> "after-" + last).map(i -> "item" + i);

        assertThat(page.items()).containsExactly("item1", "item2");
        assertThat(page.nextCursor()).isEqualTo("after-2");
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("size가 1보다 작으면 만들 수 없다")
    void invalidSize() {
        assertThatThrownBy(() -> CursorPage.of(List.of(1), 0, last -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
