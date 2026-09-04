package com.apparel.tracking.reference.repository;

import java.util.List;
import java.util.Optional;

import com.apparel.tracking.reference.domain.Branch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByActiveTrueOrderBySortOrderAsc();

    Optional<Branch> findByCode(String code);
}
