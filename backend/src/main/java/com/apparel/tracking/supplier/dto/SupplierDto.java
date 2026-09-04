package com.apparel.tracking.supplier.dto;

import com.apparel.tracking.supplier.domain.Supplier;

public record SupplierDto(Long id, String nameAr, String nameEn, String phone, String note, boolean active) {

    public static SupplierDto from(Supplier supplier) {
        return new SupplierDto(
                supplier.getId(),
                supplier.getNameAr(),
                supplier.getNameEn(),
                supplier.getPhone(),
                supplier.getNote(),
                supplier.isActive());
    }
}
