package com.kitsune.core.network.catalog

/** Explicit ranks (rather than enum ordinal) so sorting stays correct regardless of declaration order. */
val QualityTier.rank: Int
    get() = when (this) {
        QualityTier.PREMIUM -> 3
        QualityTier.HIGH -> 2
        QualityTier.STANDARD -> 1
        QualityTier.UNKNOWN -> 0
    }

val SpeedTier.rank: Int
    get() = when (this) {
        SpeedTier.FAST -> 3
        SpeedTier.MEDIUM -> 2
        SpeedTier.SLOW -> 1
        SpeedTier.UNKNOWN -> 0
    }
