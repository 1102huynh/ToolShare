package com.toolshare.listing.api;

import com.toolshare.listing.api.dto.CategoryRequest;
import com.toolshare.listing.api.dto.CategoryResponse;
import com.toolshare.listing.application.CategoryService;
import com.toolshare.listing.domain.Category;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(categoryService.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(toResponse(categoryService.findById(categoryId)));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(categoryService.create(request.key(), request.displayName())));
    }

    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID categoryId, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(toResponse(categoryService.update(categoryId, request.key(), request.displayName())));
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getKey(),
                category.getDisplayName(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}