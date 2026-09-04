package com.apparel.tracking.fabric.domain;

/**
 * Which of a fabric type's two stock pools a batch or allocation belongs to.
 * Derived from whether the intake names a derby; not stored as a column.
 */
public enum FabricPool {
    REGULAR,
    DERBY
}
