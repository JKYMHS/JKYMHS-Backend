package com.jkymhs.backend.global.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 기반 목록 응답 {@code {items, nextCursor, hasNext}}. 마지막 페이지면 nextCursor는 null, hasNext는 false.
 *
 * 다음 페이지가 있는지는 size + 1개를 조회해서 판단한다 (COUNT 쿼리를 따로 돌리지 않으려고).
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    /**
     * @param fetchedSizePlusOne size + 1개까지 조회한 결과 (size + 1번째가 있으면 다음 페이지가 있다)
     * @param size               이번 페이지 크기
     * @param cursorOf           이번 페이지 마지막 항목으로 다음 커서를 만드는 함수 (보통 {@code CursorCodec.encode(...)})
     */
    public static <T> CursorPage<T> of(List<T> fetchedSizePlusOne, int size, Function<T, String> cursorOf) {
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        boolean hasNext = fetchedSizePlusOne.size() > size;
        List<T> items = hasNext ? List.copyOf(fetchedSizePlusOne.subList(0, size)) : List.copyOf(fetchedSizePlusOne);
        String nextCursor = hasNext ? cursorOf.apply(items.get(items.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor, hasNext);
    }

    /**
     * 엔티티로 커서를 만든 뒤 응답 DTO로 바꿀 때 쓴다. 커서·hasNext는 그대로 둔다.
     */
    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}
