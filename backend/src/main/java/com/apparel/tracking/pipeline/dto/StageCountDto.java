package com.apparel.tracking.pipeline.dto;

import java.util.List;

import com.apparel.tracking.pipeline.domain.ModelBranchStageCount;

/**
 * How many pieces sit in one stage, and how that splits by size.
 *
 * <p>{@code pieceCount} and {@code flaggedCount} are the stage's totals across
 * every size, so anything reading the stage as one number still reads the right
 * one. {@code sizes} is where the detail lives: size 6 received while 8 and 10
 * are still being cut shows up as two rows here, not as one blurred total.
 */
public record StageCountDto(
        Long stageId,
        String stageCode,
        String stageNameAr,
        String stageNameEn,
        int sequenceNo,
        int pieceCount,
        int flaggedCount,
        List<SizeCountDto> sizes) {

    /** One size's pieces within a stage. A null size is the pre-sizing bucket. */
    public record SizeCountDto(
            Long sizeId,
            String sizeCode,
            int pieceCount,
            int flaggedCount) {

        public static SizeCountDto from(ModelBranchStageCount count) {
            return new SizeCountDto(
                    count.getSize() == null ? null : count.getSize().getId(),
                    count.getSize() == null ? null : count.getSize().getCode(),
                    count.getPieceCount(),
                    count.getFlaggedCount());
        }
    }

    /** Folds every size row of one stage into the stage's line. */
    public static StageCountDto of(
            Long stageId,
            String code,
            String nameAr,
            String nameEn,
            int sequenceNo,
            List<ModelBranchStageCount> rows) {

        List<SizeCountDto> sizes = rows.stream()
                .map(SizeCountDto::from)
                // Named sizes first and in code order; the unsized bucket last,
                // where it reads as the leftover it is.
                .sorted((a, b) -> {
                    if (a.sizeCode() == null) {
                        return b.sizeCode() == null ? 0 : 1;
                    }
                    return b.sizeCode() == null ? -1 : a.sizeCode().compareTo(b.sizeCode());
                })
                .toList();

        return new StageCountDto(
                stageId,
                code,
                nameAr,
                nameEn,
                sequenceNo,
                sizes.stream().mapToInt(SizeCountDto::pieceCount).sum(),
                sizes.stream().mapToInt(SizeCountDto::flaggedCount).sum(),
                sizes);
    }
}
