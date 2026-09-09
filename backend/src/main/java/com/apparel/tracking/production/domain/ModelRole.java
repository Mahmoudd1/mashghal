package com.apparel.tracking.production.domain;

/**
 * Which half of a suit a sub-model is.
 *
 * <p>Deliberately only two: a suit is a top and a bottom. A garment that comes
 * in three parts is a different thing and should not be squeezed in here — the
 * pairing arithmetic that makes "how many suits" answerable assumes two.
 */
public enum ModelRole {
    TOP,
    BOTTOM
}
