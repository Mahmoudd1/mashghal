package com.apparel.tracking.fabric.domain;

/** How to break down remaining stock. */
public enum RemainingGrouping {
    /** One row per fabric type. */
    TOTAL,
    /** One row per fabric type and intake date. */
    DATE,
    /** One row per fabric type and supplier. */
    SUPPLIER
}
