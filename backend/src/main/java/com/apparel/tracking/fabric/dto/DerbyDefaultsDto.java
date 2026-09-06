package com.apparel.tracking.fabric.dto;

import java.math.BigDecimal;

import com.apparel.tracking.fabric.domain.FabricUnit;

/**
 * What a new derby would inherit from the fabric it belongs to.
 *
 * <p>Read by the form before anything is typed, so the supplier and price arrive
 * already filled in. They are a starting point, not a rule: a derby bought from
 * someone else, or at another price, is edited on the way in.
 *
 * @param pricePerUnit null for anyone but the owner, and null when the fabric has
 *                     no purchase to inherit from yet
 */
public record DerbyDefaultsDto(
        Long supplierId,
        String supplierNameAr,
        BigDecimal pricePerUnit,
        FabricUnit unit) {

    public DerbyDefaultsDto withoutPrices() {
        return new DerbyDefaultsDto(supplierId, supplierNameAr, null, unit);
    }
}
