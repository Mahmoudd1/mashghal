package com.apparel.tracking.pipeline.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.pipeline.domain.ModelBranchStageCount;
import com.apparel.tracking.report.dto.FlaggedRowDto;
import com.apparel.tracking.report.dto.StageTotalDto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ModelBranchStageCountRepository extends JpaRepository<ModelBranchStageCount, Long> {

    Optional<ModelBranchStageCount> findByModelIdAndBranchIdAndStageId(Long modelId, Long branchId, Long stageId);

    @Query("""
            select c from ModelBranchStageCount c
              join fetch c.stage
              join fetch c.branch
              join fetch c.model
            where c.model.id = :modelId and c.branch.id = :branchId
            order by c.stage.sequenceNo asc
            """)
    List<ModelBranchStageCount> findByModelAndBranch(@Param("modelId") Long modelId, @Param("branchId") Long branchId);

    @Query("""
            select c from ModelBranchStageCount c
              join fetch c.stage
              join fetch c.branch
              join fetch c.model
            where (:modelId is null or c.model.id = :modelId)
              and (:branchId is null or c.branch.id = :branchId)
            order by c.model.modelNumber asc, c.branch.sortOrder asc, c.stage.sequenceNo asc
            """)
    List<ModelBranchStageCount> search(@Param("modelId") Long modelId, @Param("branchId") Long branchId);

    @Query("""
            select coalesce(sum(c.pieceCount), 0) from ModelBranchStageCount c
            where c.model.id = :modelId and c.branch.id = :branchId
            """)
    long totalPieces(@Param("modelId") Long modelId, @Param("branchId") Long branchId);

    /** Stage totals across every model, optionally narrowed to one branch. */
    @Query("""
            select new com.apparel.tracking.report.dto.StageTotalDto(
                s.id, s.code, s.nameAr, s.nameEn, s.sequenceNo,
                sum(c.pieceCount), sum(c.flaggedCount))
            from ModelBranchStageCount c
              join c.stage s
            where (:branchId is null or c.branch.id = :branchId)
            group by s.id, s.code, s.nameAr, s.nameEn, s.sequenceNo
            order by s.sequenceNo asc
            """)
    List<StageTotalDto> stageTotals(@Param("branchId") Long branchId);

    /** One row per branch + stage, for the all-branches overview. */
    @Query("""
            select b.id, s.id, sum(c.pieceCount), sum(c.flaggedCount)
            from ModelBranchStageCount c
              join c.branch b
              join c.stage s
            group by b.id, s.id
            """)
    List<Object[]> stageTotalsByBranch();

    /** Distinct models that have any pieces at a branch (null = anywhere). */
    @Query("""
            select count(distinct c.model.id) from ModelBranchStageCount c
            where (:branchId is null or c.branch.id = :branchId)
              and c.pieceCount > 0
            """)
    long countModelsWithPieces(@Param("branchId") Long branchId);

    /** Every model + branch + stage holding at least one flagged piece. */
    @Query("""
            select new com.apparel.tracking.report.dto.FlaggedRowDto(
                m.id, m.modelNumber, m.nameAr, m.nameEn,
                b.id, b.code, b.nameAr, b.nameEn,
                s.id, s.code,
                sum(c.flaggedCount), sum(c.pieceCount))
            from ModelBranchStageCount c
              join c.model m
              join c.branch b
              join c.stage s
            where c.flaggedCount > 0
              and (:modelId is null or m.id = :modelId)
              and (:branchId is null or b.id = :branchId)
            group by m.id, m.modelNumber, m.nameAr, m.nameEn, b.id, b.code, b.nameAr, b.nameEn, s.id, s.code
            order by sum(c.flaggedCount) desc, m.modelNumber asc
            """)
    List<FlaggedRowDto> flaggedRows(@Param("modelId") Long modelId, @Param("branchId") Long branchId);
}
