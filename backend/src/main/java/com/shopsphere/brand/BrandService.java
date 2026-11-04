package com.shopsphere.brand;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.common.exception.ResourceNotFound;
import com.shopsphere.brand.repository.BrandRepository;

/** Public brand read-side (catalog facets + logo wall). */
@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<BrandSummary> listActive() {
        return brandRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(b -> new BrandSummary(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BrandSummary bySlug(String slug) {
        return brandRepository.findBySlug(slug)
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .map(b -> new BrandSummary(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl()))
                .orElseThrow(() -> ResourceNotFound.of("Brand"));
    }

    public record BrandSummary(Long id, String name, String slug, String logoUrl) {}
}
