package com.jkymhs.backend.domain.category.repository;

import com.jkymhs.backend.domain.category.entity.Category;
import com.jkymhs.backend.domain.category.entity.CategoryGroupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    List<Category> findAllByGroupTypeInOrderBySortOrderAscIdAsc(Collection<CategoryGroupType> groupTypes);
}
