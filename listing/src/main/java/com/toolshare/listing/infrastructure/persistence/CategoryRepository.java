package com.toolshare.listing.infrastructure.persistence;

import com.toolshare.listing.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    boolean existsByKey(String key);
    Optional<Category> findByKey(String key);
}