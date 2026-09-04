package com.apparel.tracking.size.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.size.domain.SizeCategory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SizeCategoryRepository extends JpaRepository<SizeCategory, Long> {

    List<SizeCategory> findAllByOrderBySortOrderAsc();

    List<SizeCategory> findAllByActiveTrueOrderBySortOrderAsc();

    Optional<SizeCategory> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);
}
