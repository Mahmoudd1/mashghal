package com.apparel.tracking.production.domain;

/**
 * A cutting run accepts allocations only while OPEN. Closing it fixes what it
 * contributed; a model may later be fed by a different main cut.
 */
public enum CutStatus {
    OPEN,
    CLOSED
}
