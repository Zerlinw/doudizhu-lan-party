package com.ddz.shared.game.core

import kotlinx.serialization.Serializable

/**
 * Table rules.
 *
 * [TIAN_DI_LAIZI] is the 欢乐斗地主-style 天地癞子 mode:
 * - a 天癞子 rank is selected before the deal (4 wild cards),
 * - a 地癞子 rank is selected after the landlord is decided (another 4 wild cards),
 * - both kinds can stand in for any non-joker rank.
 */
@Serializable
enum class GameMode(val label: String) {
    CLASSIC("经典"),
    TIAN_DI_LAIZI("天地癞子"),
}

/**
 * The two wild-card ranks of a laizi round. [diRank] is null until a landlord
 * has been chosen; both ranks' four cards are wild.
 */
@Serializable
data class LaiziInfo(
    val tianRank: Rank,
    val diRank: Rank? = null,
) {
    /** Ranks whose cards currently act as wild cards. */
    val ranks: Set<Rank>
        get() = if (diRank == null) setOf(tianRank) else setOf(tianRank, diRank)

    val allRevealed: Boolean get() = diRank != null
}
