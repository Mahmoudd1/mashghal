package com.apparel.tracking.production.repository;

import java.util.List;

import com.apparel.tracking.production.domain.Model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelRepository extends JpaRepository<Model, Long> {

    List<Model> findAllByOrderByModelNumberAsc();

    boolean existsByModelNumberIgnoreCase(String modelNumber);

    java.util.Optional<Model> findByModelNumberIgnoreCase(String modelNumber);
}
