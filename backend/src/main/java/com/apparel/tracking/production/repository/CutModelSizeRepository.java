package com.apparel.tracking.production.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.production.domain.CutModelSize;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CutModelSizeRepository extends JpaRepository<CutModelSize, Long> {

    @Query("""
            select s from CutModelSize s
              join fetch s.size gs
              join fetch gs.category
              join fetch s.model
            where s.cut.id = :cutId
            order by s.model.modelNumber asc, gs.sortOrder asc
            """)
    List<CutModelSize> findByCut(@Param("cutId") Long cutId);

    List<CutModelSize> findByCutIdAndModelId(Long cutId, Long modelId);

    Optional<CutModelSize> findByCutIdAndModelIdAndSizeId(Long cutId, Long modelId, Long sizeId);

    boolean existsBySizeId(Long sizeId);

    /** Pieces-per-layer summed per model on a cut: [modelId, piecesPerLayer]. */
    @Query("""
            select s.model.id, sum(s.piecesPerLayer)
            from CutModelSize s
            where s.cut.id = :cutId
            group by s.model.id
            """)
    List<Object[]> piecesPerLayerByModel(@Param("cutId") Long cutId);

    /** Pieces-per-layer per cut and model: [cutId, modelId, modelNumber, nameAr, perLayer]. */
    @Query("""
            select s.cut.id, m.id, m.modelNumber, m.nameAr, sum(s.piecesPerLayer)
            from CutModelSize s
              join s.model m
            group by s.cut.id, m.id, m.modelNumber, m.nameAr
            """)
    List<Object[]> piecesPerLayerByCutAndModel();

    /**
     * Models that have been cut in a given size category, for category reporting.
     * Rows: [modelId, modelNumber, nameAr, categoryId, categoryNameAr, piecesPerLayer].
     */
    @Query("""
            select m.id, m.modelNumber, m.nameAr, cat.id, cat.nameAr, sum(s.piecesPerLayer)
            from CutModelSize s
              join s.model m
              join s.size gs
              join gs.category cat
            where (:categoryId is null or cat.id = :categoryId)
            group by m.id, m.modelNumber, m.nameAr, cat.id, cat.nameAr
            order by m.modelNumber asc
            """)
    List<Object[]> modelsBySizeCategory(@Param("categoryId") Long categoryId);
}
