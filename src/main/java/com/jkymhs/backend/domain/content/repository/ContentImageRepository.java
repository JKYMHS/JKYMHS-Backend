package com.jkymhs.backend.domain.content.repository;

import com.jkymhs.backend.domain.content.entity.ContentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContentImageRepository extends JpaRepository<ContentImage, Long> {

    @Query("select i from ContentImage i where i.content.id = :contentId order by i.sortOrder")
    List<ContentImage> findAllByContentId(Long contentId);
}
