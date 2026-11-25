package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.category.entity.CategoryGroupType;
import com.jkymhs.backend.domain.category.entity.CategoryMapping;
import com.jkymhs.backend.domain.category.entity.CategoryTab;
import com.jkymhs.backend.domain.category.repository.CategoryMappingRepository;
import com.jkymhs.backend.domain.category.repository.CategoryRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    static final String INVALID_FILTER_MESSAGE = "잘못된 필터 값입니다.";

    private static final List<CategoryGroupType> FILTER_GROUPS =
            List.of(CategoryGroupType.PLACE_FILTER, CategoryGroupType.FESTIVAL_FILTER);

    private final CategoryRepository categoryRepository;
    private final CategoryMappingRepository categoryMappingRepository;

    public List<CategoryRef> getFilterCategories(CategoryTab tab) {
        List<CategoryGroupType> groups = tab == null ? FILTER_GROUPS : List.of(tab.groupType());
        return categoryRepository.findAllByGroupTypeInOrderBySortOrderAscIdAsc(groups).stream()
                .sorted(Comparator.comparingInt((Category c) -> FILTER_GROUPS.indexOf(c.getGroupType())))
                .map(CategoryRef::from)
                .toList();
    }

    public CategoryRef getCategory(Long id) {
        if (id == null) {
            throw invalidFilter("카테고리 id 없음");
        }
        return categoryRepository.findById(id)
                .map(CategoryRef::from)
                .orElseThrow(() -> invalidFilter("존재하지 않는 카테고리: " + id));
    }

    public Set<String> lclsCodesOf(String categoryCode) {
        Category category = categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> invalidFilter("존재하지 않는 카테고리 코드: " + categoryCode));
        return categoryMappingRepository.findAllByCategoryId(category.getId()).stream()
                .map(CategoryMapping::getLclsCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public CategoryMatcher matcher() {
        List<Category> categories = categoryRepository.findAllByGroupTypeInOrderBySortOrderAscIdAsc(FILTER_GROUPS);
        Map<Long, Set<String>> codes = new HashMap<>();
        List<Long> ids = categories.stream().map(Category::getId).toList();
        if (!ids.isEmpty()) {
            for (CategoryMapping mapping : categoryMappingRepository.findAllByCategoryIdIn(ids)) {
                codes.computeIfAbsent(mapping.getCategory().getId(), k -> new HashSet<>()).add(mapping.getLclsCode());
            }
        }
        List<CategoryMatcher.Entry> entries = new ArrayList<>();
        for (Category category : categories) {
            entries.add(new CategoryMatcher.Entry(category.getId(), category.getGroupType(),
                    Set.copyOf(codes.getOrDefault(category.getId(), Set.of()))));
        }
        return new CategoryMatcher(entries);
    }

    private static CustomException invalidFilter(String description) {
        return new CustomException(ErrorCode.INVALID_INPUT, INVALID_FILTER_MESSAGE, description);
    }
}
