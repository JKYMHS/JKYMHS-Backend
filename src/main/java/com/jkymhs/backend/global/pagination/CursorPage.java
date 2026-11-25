package com.jkymhs.backend.global.pagination;

import java.util.List;
import java.util.function.Function;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static <T> CursorPage<T> of(List<T> fetchedSizePlusOne, int size, Function<T, String> cursorOf) {
        if (size < 1) {
            throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
        }
        boolean hasNext = fetchedSizePlusOne.size() > size;
        List<T> items = hasNext ? List.copyOf(fetchedSizePlusOne.subList(0, size)) : List.copyOf(fetchedSizePlusOne);
        String nextCursor = hasNext ? cursorOf.apply(items.get(items.size() - 1)) : null;
        return new CursorPage<>(items, nextCursor, hasNext);
    }

    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor, hasNext);
    }
}
