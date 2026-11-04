package com.shopsphere.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.category.entity.Category;
import com.shopsphere.category.repository.CategoryRepository;

/**
 * Public catalog navigation. Categories are read-only here — mutation happens
 * in the admin module only.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategorySummary> listActive() {
        return categoryRepository.findByParentIsNullAndIsActiveTrueOrderByNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategorySummary bySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .map(this::toSummary)
                .orElseThrow(() -> ResourceNotFound.of("Category"));
    }

    CategorySummary toSummary(Category category) {
        return new CategorySummary(category.getId(), category.getName(), category.getSlug(),
                category.getImageUrl(), category.getParent() == null ? null : category.getParent().getId(),
                categoryRepository.countByParentIdAndIsActiveTrue(category.getId()));
    }

    /** Public projection — slug + child count for the nav tree. */
    public record CategorySummary(Long id, String name, String slug, String imageUrl,
                                  Long parentId, long childCount) {}
}
