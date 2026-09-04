package com.apparel.tracking.size.service;

import java.util.List;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.exception.NotFoundException;
import com.apparel.tracking.production.repository.CutModelSizeRepository;
import com.apparel.tracking.size.domain.GarmentSize;
import com.apparel.tracking.size.domain.SizeCategory;
import com.apparel.tracking.size.dto.GarmentSizeDto;
import com.apparel.tracking.size.dto.GarmentSizeRequest;
import com.apparel.tracking.size.dto.ModelSizeCategoryRowDto;
import com.apparel.tracking.size.dto.SizeCategoryDto;
import com.apparel.tracking.size.dto.SizeCategoryRequest;
import com.apparel.tracking.size.repository.GarmentSizeRepository;
import com.apparel.tracking.size.repository.SizeCategoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Size categories and the sizes within them.
 *
 * <p>Editable master data, but changes are expected to be rare — the categories
 * exist mainly so reporting can ask about a whole range at once.
 */
@Service
@Transactional
public class SizeService {

    private final SizeCategoryRepository categories;
    private final GarmentSizeRepository sizes;
    private final CutModelSizeRepository cutModelSizes;

    public SizeService(
            SizeCategoryRepository categories,
            GarmentSizeRepository sizes,
            CutModelSizeRepository cutModelSizes) {
        this.categories = categories;
        this.sizes = sizes;
        this.cutModelSizes = cutModelSizes;
    }

    @Transactional(readOnly = true)
    public List<SizeCategoryDto> listCategories(boolean activeOnly) {
        List<SizeCategory> found = activeOnly
                ? categories.findAllByActiveTrueOrderBySortOrderAsc()
                : categories.findAllByOrderBySortOrderAsc();
        return found.stream().map(SizeCategoryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GarmentSizeDto> listSizes() {
        return sizes.findAllByActiveTrueOrderBySortOrderAsc().stream().map(GarmentSizeDto::from).toList();
    }

    /** Which models have been cut in a given size range. */
    @Transactional(readOnly = true)
    public List<ModelSizeCategoryRowDto> modelsByCategory(Long categoryId) {
        return cutModelSizes.modelsBySizeCategory(categoryId).stream()
                .map(row -> new ModelSizeCategoryRowDto(
                        (Long) row[0], (String) row[1], (String) row[2],
                        (Long) row[3], (String) row[4], ((Number) row[5]).longValue()))
                .toList();
    }

    public SizeCategoryDto createCategory(SizeCategoryRequest request) {
        if (categories.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessRuleException("size_category_code_taken",
                    "A size category with code '%s' already exists".formatted(request.code()));
        }
        SizeCategory category = new SizeCategory();
        category.setCode(request.code());
        apply(category, request);
        return SizeCategoryDto.from(categories.save(category));
    }

    public SizeCategoryDto updateCategory(Long id, SizeCategoryRequest request) {
        SizeCategory category = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("Size category", id));
        if (!category.getCode().equalsIgnoreCase(request.code())
                && categories.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessRuleException("size_category_code_taken",
                    "A size category with code '%s' already exists".formatted(request.code()));
        }
        category.setCode(request.code());
        apply(category, request);
        return SizeCategoryDto.from(category);
    }

    public GarmentSizeDto createSize(GarmentSizeRequest request) {
        if (sizes.existsByCodeIgnoreCase(request.code())) {
            throw new BusinessRuleException("size_code_taken",
                    "A size with code '%s' already exists".formatted(request.code()));
        }
        SizeCategory category = categories.findById(request.sizeCategoryId())
                .orElseThrow(() -> NotFoundException.of("Size category", request.sizeCategoryId()));

        GarmentSize size = new GarmentSize();
        size.setCategory(category);
        size.setCode(request.code());
        size.setNameAr(request.nameAr());
        size.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        size.setActive(request.active() == null || request.active());
        return GarmentSizeDto.from(sizes.save(size));
    }

    public void deleteSize(Long id) {
        GarmentSize size = sizes.findById(id).orElseThrow(() -> NotFoundException.of("Size", id));
        if (cutModelSizes.existsBySizeId(id)) {
            throw new BusinessRuleException("size_in_use",
                    "This size appears on a cut; deactivate it instead of deleting");
        }
        sizes.delete(size);
    }

    private void apply(SizeCategory category, SizeCategoryRequest request) {
        category.setNameAr(request.nameAr());
        category.setNameEn(request.nameEn());
        category.setNote(request.note());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        if (request.active() != null) {
            category.setActive(request.active());
        }
    }
}
