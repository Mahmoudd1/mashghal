package com.apparel.tracking.pipeline.domain;

/** Why pieces moved. Distinguishes plan changes from real production progress. */
public enum MovementReason {
    /** Pieces entered the pipeline because a cut allocated them to this model. */
    ALLOCATION_ADDED,
    /** Pieces left the pipeline because an allocation was reduced. */
    ALLOCATION_REDUCED,
    /** A plain move between two stages. */
    STAGE_MOVE,
    /** The receiving action: pieces arrived from the branch. */
    RECEIVING,
    /** Pieces moved from RECEIVED to SOLD. */
    SALE
}
