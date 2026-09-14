package com.apparel.tracking.production.domain;

/**
 * How a cut's fabric was written down.
 *
 * <p>{@link #DETAILED} builds the cut from one row per physical roll and derives
 * every total from them — the record of someone weighing rolls at the table.
 * {@link #SUMMARY} carries the totals themselves, for a cut written up afterwards
 * from a paper sheet, and draws its fabric from the batches oldest-first.
 *
 * <p>Fixed once a cut carries data: counting the same fabric from both sides
 * would double it.
 */
public enum CutEntryMode {
    DETAILED,
    SUMMARY
}
