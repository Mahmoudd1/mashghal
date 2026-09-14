package com.apparel.tracking.production.domain;

import com.apparel.tracking.common.exception.BusinessRuleException;
import com.apparel.tracking.common.model.BaseEntity;
import com.apparel.tracking.fabric.domain.FabricType;
import com.apparel.tracking.reference.domain.Branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One physical cutting run.
 *
 * <p>A cut is not owned by a model — see {@link CutModelAllocation}. It does
 * belong to a branch: the site where the fabric was physically cut, which may
 * differ from the branch that sews the resulting pieces.
 *
 * <p>A cut carries no flagged-piece count. Defects are recorded against a
 * model's pipeline, because one cutting run feeds several models and a
 * cut-level count could not be attributed to any one of them.
 */
@Entity
@Table(name = "cut")
@Getter
@Setter
@NoArgsConstructor
public class Cut extends BaseEntity {

    @Column(name = "cut_number", nullable = false, length = 64)
    private String cutNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "cut_type", nullable = false, length = 16)
    private CutType cutType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_main_cut_id")
    private Cut parentMainCut;

    /**
     * Always {@code MAIN} when a parent is set, otherwise null. Redundant with
     * the parent's own type on purpose: it lets a composite foreign key enforce
     * "the parent must be a MAIN cut" in the database rather than only in code.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "parent_cut_type", length = 16)
    private CutType parentCutType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CutStatus status = CutStatus.OPEN;

    @Column(name = "cut_date", nullable = false)
    private LocalDate cutDate;

    /** One cutting run lays out one fabric type; every roll on it must match. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fabric_type_id")
    private FabricType fabricType;

    /** Length of fabric laid out per layer. A recorded measurement, not derived. */
    @Column(name = "cut_length", precision = 12, scale = 3)
    private BigDecimal cutLength;

    @Column(name = "model_description", length = 512)
    private String modelDescription;

    /**
     * The model this cut was opened for. Nearly every cut is a new model, so it
     * is named on the cut and created from it; the size breakdown then already
     * knows which model it belongs to. Further models can still be added.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_model_id")
    private Model primaryModel;

    @Column(name = "label_ar", length = 128)
    private String labelAr;

    @Column(name = "label_en", length = 128)
    private String labelEn;

    @Column(name = "note", length = 512)
    private String note;

    /**
     * Whether this cut's fabric is recorded roll by roll or as totals.
     *
     * <p>Fixed once the cut carries data, because the two ways describe the same
     * fabric and adding them together would double it.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_mode", nullable = false, length = 16)
    private CutEntryMode entryMode = CutEntryMode.DETAILED;

    /** Rolls laid out, including any that were already part-used. */
    @Column(name = "total_rolls")
    private Integer totalRolls;

    /**
     * How many of {@link #totalRolls} were already open before this cut.
     *
     * <p>Those were drawn off their batches by an earlier cut, so only the
     * remainder counts against a batch's roll count here.
     */
    @Column(name = "reused_rolls")
    private Integer reusedRolls;

    /** Fabric taken off the shelf, waste included. */
    @Column(name = "total_weight", precision = 14, scale = 3)
    private BigDecimal totalWeight;

    /** The عجز: the part of {@link #totalWeight} that was binned, not cut. */
    @Column(name = "waste_weight", precision = 14, scale = 3)
    private BigDecimal wasteWeight;

    @Column(name = "total_layers")
    private Integer totalLayers;

    public boolean isSummary() {
        return entryMode == CutEntryMode.SUMMARY;
    }

    /** Rolls this cut took off a batch: the ones that were not already open. */
    public int newRolls() {
        return totalRolls == null ? 0 : totalRolls - (reusedRolls == null ? 0 : reusedRolls);
    }

    /** Fabric that became garments — what was taken, less what was binned. */
    public BigDecimal consumedWeight() {
        return totalWeight == null ? BigDecimal.ZERO : totalWeight.subtract(wasteWeight);
    }

    public boolean isOpen() {
        return status == CutStatus.OPEN;
    }

    /** Keeps {@code parentCutType} consistent with the parent reference. */
    public void assignParent(Cut parent) {
        if (parent == null) {
            parentMainCut = null;
            parentCutType = null;
            return;
        }
        if (parent.getCutType() != CutType.MAIN) {
            throw new BusinessRuleException("cut_parent_not_main",
                    "A %s cut must reference a MAIN cut".formatted(cutType));
        }
        parentMainCut = parent;
        parentCutType = CutType.MAIN;
    }

    public void requireOpen() {
        if (!isOpen()) {
            throw new BusinessRuleException("cut_closed",
                    "Cut %s is closed and no longer accepts allocations".formatted(cutNumber));
        }
    }
}
