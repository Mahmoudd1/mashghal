package com.apparel.tracking.fabric.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.fabric.domain.Derby;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DerbyRepository extends JpaRepository<Derby, Long> {

    Optional<Derby> findByFabricTypeId(Long fabricTypeId);

    boolean existsByFabricTypeId(Long fabricTypeId);

    List<Derby> findAllByFabricTypeIdIn(List<Long> fabricTypeIds);
}
