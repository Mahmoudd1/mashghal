package com.apparel.tracking.auth.domain;

/**
 * OWNER sees everything, including what the business pays for fabric.
 * ADMIN runs the system — master data, cuts, users — but not the money.
 * DATA_ENTRY records production activity and reads reports.
 *
 * <p>The split exists so a manager can be given full operational control without
 * being shown purchase prices, costs or supplier price comparisons.
 */
public enum UserRole {
    OWNER,
    ADMIN,
    DATA_ENTRY;

    /** Spring Security's convention: authorities carry a {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
