package com.apparel.tracking.size.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.size.domain.GarmentSize;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GarmentSizeRepository extends JpaRepository<GarmentSize, Long> {

    List<GarmentSize> findAllByActiveTrueOrderBySortOrderAsc();

    List<GarmentSize> findAllByCategoryIdOrderBySortOrderAsc(Long categoryId);

    Optional<GarmentSize> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
