package com.apparel.tracking.supplier.repository;

import java.util.List;

import com.apparel.tracking.supplier.domain.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameArAsc();

    List<Supplier> findAllByActiveTrueOrderByNameArAsc();

    boolean existsByNameArIgnoreCase(String nameAr);
}
