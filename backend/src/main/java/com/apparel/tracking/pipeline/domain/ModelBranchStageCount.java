package com.apparel.tracking.pipeline.domain;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.production.domain.Model;
import com.apparel.tracking.reference.domain.Branch;
import com.apparel.tracking.reference.domain.PipelineStage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How many pieces of one model sit in one stage at one branch.
 *
 * <p>{@code flaggedCount} is a tag on pieces already counted in
 * {@code pieceCount}, not a separate bucket — flagged pieces stay in the stage
 * total so the reconciliation sum is unaffected.
 */
@Entity
@Table(name = "model_branch_stage_count")
@Getter
@Setter
@NoArgsConstructor
public class ModelBranchStageCount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private PipelineStage stage;

    @Column(name = "piece_count", nullable = false)
    private int pieceCount;

    @Column(name = "flagged_count", nullable = false)
    private int flaggedCount;

    /** Pieces here that are not flagged as defective, and so can still be sold. */
    public int sellableCount() {
        return pieceCount - flaggedCount;
    }

    public void add(int quantity) {
        pieceCount += quantity;
    }

    /**
     * Removes {@code quantity} pieces, keeping the flag tag consistent: if the
     * stage no longer holds enough pieces to carry its flags, the flag count is
     * trimmed to match.
     */
    public void remove(int quantity) {
        if (quantity > pieceCount) {
            throw new BusinessRuleException("stage_insufficient_pieces",
                    "%s at %s holds %d pieces, cannot remove %d"
                            .formatted(stage.getCode(), branch.getCode(), pieceCount, quantity));
        }
        pieceCount -= quantity;
        if (flaggedCount > pieceCount) {
            flaggedCount = pieceCount;
        }
    }

    public void flag(int quantity) {
        if (flaggedCount + quantity > pieceCount) {
            throw new BusinessRuleException("flagged_exceeds_stage_count",
                    "%s holds %d pieces with %d already flagged, cannot flag %d more"
                            .formatted(stage.getCode(), pieceCount, flaggedCount, quantity));
        }
        flaggedCount += quantity;
    }

    public void unflag(int quantity) {
        if (quantity > flaggedCount) {
            throw new BusinessRuleException("unflag_exceeds_flagged",
                    "%s has only %d flagged pieces, cannot clear %d"
                            .formatted(stage.getCode(), flaggedCount, quantity));
        }
        flaggedCount -= quantity;
    }
}
