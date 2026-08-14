package com.toolshare.listing.application;

import com.toolshare.common.exception.ConflictException;
import com.toolshare.common.exception.ForbiddenException;
import com.toolshare.common.exception.ResourceNotFoundException;
import com.toolshare.identity.authorization.AuthorizationService;
import com.toolshare.identity.authorization.Permission;
import com.toolshare.identity.domain.IdentityAccount;
import com.toolshare.listing.domain.Category;
import com.toolshare.listing.infrastructure.persistence.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuthenticatedAccountResolver authenticatedAccountResolver;
    private final AuthorizationService authorizationService;

    public CategoryService(
            CategoryRepository categoryRepository,
            AuthenticatedAccountResolver authenticatedAccountResolver,
            AuthorizationService authorizationService
    ) {
        this.categoryRepository = categoryRepository;
        this.authenticatedAccountResolver = authenticatedAccountResolver;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        requireAdmin();
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Category findById(UUID categoryId) {
        requireAdmin();
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Transactional
    public Category create(String key, String displayName) {
        requireAdmin();
        String normalizedKey = Category.normalizeKey(key);
        if (categoryRepository.existsByKey(normalizedKey)) {
            throw new ConflictException("Category with this key already exists");
        }
        return categoryRepository.save(new Category(normalizedKey, displayName));
    }

    @Transactional
    public Category update(UUID categoryId, String key, String displayName) {
        requireAdmin();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        String normalizedKey = Category.normalizeKey(key);
        categoryRepository.findByKey(normalizedKey)
                .filter(existing -> !existing.getId().equals(categoryId))
                .ifPresent(existing -> {
                    throw new ConflictException("Category with this key already exists");
                });

        category.update(normalizedKey, displayName);
        return categoryRepository.save(category);
    }

    private void requireAdmin() {
        IdentityAccount account = authenticatedAccountResolver.requireCurrentAccount();
        if (!authorizationService.hasPermission(account, Permission.ADMIN_ACCESS)) {
            throw new ForbiddenException("Admin permission is required");
        }
    }
}