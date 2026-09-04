package com.apparel.tracking.reference.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.reference.domain.PipelineStage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    List<PipelineStage> findAllByActiveTrueOrderBySequenceNoAsc();

    Optional<PipelineStage> findByCode(String code);
}
