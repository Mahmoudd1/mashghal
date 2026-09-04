package com.apparel.tracking.fabric.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.fabric.domain.FabricType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricTypeRepository extends JpaRepository<FabricType, Long> {

    List<FabricType> findAllByOrderByNameArAsc();

    List<FabricType> findAllByActiveTrueOrderByNameArAsc();

    Optional<FabricType> findByNameAr(String nameAr);

    boolean existsByNameArIgnoreCase(String nameAr);
}
