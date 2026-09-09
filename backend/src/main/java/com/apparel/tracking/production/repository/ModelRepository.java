package com.apparel.tracking.production.repository;

import java.util.List;

import com.apparel.tracking.production.domain.Model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ModelRepository extends JpaRepository<Model, Long> {

    List<Model> findAllByOrderByModelNumberAsc();

    boolean existsByModelNumberIgnoreCase(String modelNumber);

    java.util.Optional<Model> findByModelNumberIgnoreCase(String modelNumber);

    /** The two halves of one suit. */
    List<Model> findByParentModelIdOrderByRoleAsc(Long parentModelId);

    /** Every model that is half of a suit, with its suit fetched. */
    @Query("""
            select m from Model m
              join fetch m.parentModel p
            where m.parentModel is not null
            order by p.modelNumber asc, m.role asc
            """)
    List<Model> findAllSuitParts();
}
