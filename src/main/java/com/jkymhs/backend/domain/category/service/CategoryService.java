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

/**
 * 카테고리 조회. 다른 도메인(탐색, 콘텐츠 적재)은 categories·category_mappings를 직접 읽지 않고 이 서비스만 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    static final String INVALID_FILTER_MESSAGE = "잘못된 필터 값입니다.";

    private static final List<CategoryGroupType> FILTER_GROUPS =
            List.of(CategoryGroupType.PLACE_FILTER, CategoryGroupType.FESTIVAL_FILTER);

    private final CategoryRepository categoryRepository;
    private final CategoryMappingRepository categoryMappingRepository;

    /**
     * 탐색 필터 카테고리. 칩(CHIP)은 홈 화면 고정 버튼이라 목록에 넣지 않는다.
     *
     * @param tab null이면 두 탭 모두 (장소 → 축제 순)
     */
    public List<CategoryRef> getFilterCategories(CategoryTab tab) {
        List<CategoryGroupType> groups = tab == null ? FILTER_GROUPS : List.of(tab.groupType());
        return categoryRepository.findAllByGroupTypeInOrderBySortOrderAscIdAsc(groups).stream()
                .sorted(Comparator.comparingInt((Category c) -> FILTER_GROUPS.indexOf(c.getGroupType())))
                .map(CategoryRef::from)
                .toList();
    }

    /**
     * @throws CustomException 400 INVALID_INPUT "잘못된 필터 값입니다." — 없는 카테고리
     */
    public CategoryRef getCategory(Long id) {
        if (id == null) {
            throw invalidFilter("카테고리 id 없음");
        }
        return categoryRepository.findById(id)
                .map(CategoryRef::from)
                .orElseThrow(() -> invalidFilter("존재하지 않는 카테고리: " + id));
    }

    /**
     * 카테고리·칩 코드에 묶인 분류 코드들 (콘텐츠의 lcls1/2/3 중 하나가 이 안에 있으면 그 카테고리다).
     *
     * @throws CustomException 400 INVALID_INPUT "잘못된 필터 값입니다." — 없는 코드
     */
    public Set<String> lclsCodesOf(String categoryCode) {
        Category category = categoryRepository.findByCode(categoryCode)
                .orElseThrow(() -> invalidFilter("존재하지 않는 카테고리 코드: " + categoryCode));
        return categoryMappingRepository.findAllByCategoryId(category.getId()).stream()
                .map(CategoryMapping::getLclsCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 콘텐츠 적재용 변환표. 필터 카테고리 전체와 매핑을 한 번에 읽는다. */
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
