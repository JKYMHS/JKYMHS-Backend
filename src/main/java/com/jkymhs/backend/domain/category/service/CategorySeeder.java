package com.jkymhs.backend.domain.category.service;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.category.entity.CategoryGroupType;
import com.jkymhs.backend.domain.category.entity.CategoryMapping;
import com.jkymhs.backend.domain.category.repository.CategoryMappingRepository;
import com.jkymhs.backend.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 서버가 뜰 때마다 {@link CategorySeed}를 DB에 맞춘다. 코드(code)로 찾아서 없으면 넣고, 있으면 이름·순서를 고친다 (id는 유지).
 * 매핑도 시드와 같아지도록 빠진 것은 넣고 시드에 없는 것은 지운다. 몇 번을 돌려도 결과가 같다.
 *
 * 순서가 1번인 이유: CSV 적재(TourCsvImporter, 2번)가 콘텐츠의 category_id를 정할 때 카테고리가 이미 있어야 한다.
 * 테스트는 매 테스트 뒤 테이블을 비우므로, 카테고리가 필요한 테스트는 {@link #seed()}를 직접 부른다.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CategorySeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final CategoryMappingRepository categoryMappingRepository;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public void seed() {
        Map<CategoryGroupType, Integer> sortOrders = new EnumMap<>(CategoryGroupType.class);
        Map<String, Category> byCode = new HashMap<>();
        for (Category category : categoryRepository.findAll()) {
            byCode.put(category.getCode(), category);
        }
        for (CategorySeed.Item item : CategorySeed.ITEMS) {
            int sortOrder = sortOrders.merge(item.groupType(), 1, Integer::sum);
            Category category = byCode.get(item.code());
            if (category == null) {
                category = categoryRepository.save(Category.create(item.code(), item.name(), item.groupType(), sortOrder));
            } else {
                category.update(item.name(), item.groupType(), sortOrder);
            }
            syncMappings(category, item.lclsCodes());
        }
    }

    private void syncMappings(Category category, List<String> seedCodes) {
        List<CategoryMapping> existing = categoryMappingRepository.findAllByCategoryId(category.getId());
        Set<String> existingCodes = new HashSet<>();
        for (CategoryMapping mapping : existing) {
            if (seedCodes.contains(mapping.getLclsCode())) {
                existingCodes.add(mapping.getLclsCode());
            } else {
                categoryMappingRepository.delete(mapping);
            }
        }
        for (String code : seedCodes) {
            if (!existingCodes.contains(code)) {
                categoryMappingRepository.save(CategoryMapping.create(category, code));
            }
        }
    }
}
