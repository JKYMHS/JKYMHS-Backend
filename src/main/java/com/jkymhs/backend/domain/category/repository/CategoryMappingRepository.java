package com.jkymhs.backend.domain.category.repository;

import com.jkymhs.backend.domain.category.entity.CategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, Long> {

    @Query("select m from CategoryMapping m where m.category.id = :categoryId")
    List<CategoryMapping> findAllByCategoryId(Long categoryId);

    @Query("select m from CategoryMapping m where m.category.id in :categoryIds")
    List<CategoryMapping> findAllByCategoryIdIn(Collection<Long> categoryIds);
}
