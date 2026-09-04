package com.apparel.tracking.production.domain;

/** Kind of cutting run. SECONDARY and DERBY always hang off a MAIN cut. */
public enum CutType {
    MAIN,
    SECONDARY,
    DERBY;

    public boolean requiresParent() {
        return this != MAIN;
    }
}
