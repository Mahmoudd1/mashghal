package com.apparel.tracking.fabric.repository;

import java.util.List;

import com.apparel.tracking.fabric.domain.FabricColor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricColorRepository extends JpaRepository<FabricColor, Long> {

    List<FabricColor> findAllByFabricTypeIdOrderByNameArAsc(Long fabricTypeId);

    boolean existsByFabricTypeIdAndNameArIgnoreCase(Long fabricTypeId, String nameAr);
}
