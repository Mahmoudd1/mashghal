package com.apparel.tracking.fabric.repository;

import java.util.Optional;

import com.apparel.tracking.fabric.domain.FabricIntakeColor;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FabricIntakeColorRepository extends JpaRepository<FabricIntakeColor, Long> {

    Optional<FabricIntakeColor> findByIntakeIdAndColorId(Long intakeId, Long colorId);

    boolean existsByColorId(Long colorId);
}
