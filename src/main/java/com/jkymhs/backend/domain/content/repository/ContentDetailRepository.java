package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.ContentDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentDetailRepository extends JpaRepository<ContentDetail, Long> {
}
