package com.apparel.tracking.fabric.dto;

import com.apparel.tracking.fabric.domain.Derby;

public record DerbyDto(Long id, Long fabricTypeId, String fabricTypeNameAr, String note) {

    public static DerbyDto from(Derby derby) {
        return new DerbyDto(
                derby.getId(),
                derby.getFabricType().getId(),
                derby.getFabricType().getNameAr(),
                derby.getNote());
    }
}
