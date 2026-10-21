package com.ddz.shared.game.core

import kotlinx.serialization.Serializable

/**
 * All legal 斗地主 hand shapes. [label] is presentation text only.
 */
@Serializable
enum class HandType(val label: String) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_ONE("三带一"),
    TRIPLE_TWO("三带二"),
    STRAIGHT("顺子"),
    DOUBLE_STRAIGHT("连对"),
    AIRPLANE("飞机"),
    AIRPLANE_WINGS("飞机带翅膀"),
    FOUR_TWO("四带二"),
    FOUR_TWO_PAIR("四带两对"),
    BOMB("炸弹"),
    SOFT_BOMB("软炸"),
    PURE_LAIZI_BOMB("癞子炸"),
    ROCKET("王炸"),
    ;

    /** True for every bomb family member, including the rocket. */
    val isBomb: Boolean
        get() = this == BOMB || this == SOFT_BOMB || this == PURE_LAIZI_BOMB || this == ROCKET

    /**
     * Bomb ordering within the same card count:
     * 软炸(0) < 硬炸(1) < 纯癞子炸(2) < 王炸(3).
     */
    val bombTier: Int
        get() = when (this) {
            SOFT_BOMB -> 0
            BOMB -> 1
            PURE_LAIZI_BOMB -> 2
            ROCKET -> 3
            else -> -1
        }
}

/** Shape of the kickers attached to an airplane / triple. */
@Serializable
enum class WingKind { NONE, SINGLE, PAIR }

/**
 * A parsed, playable combination.
 *
 * [chain] is the number of consecutive groups (straight length, number of
 * consecutive pairs, number of consecutive triples). [mainRank] is the
 * comparison key of the leading group. [wing] distinguishes 三带一 from
 * 三带二 and 飞机带单 from 飞机带对.
 */
@Serializable
data class Hand(
    val cards: List<Card>,
    val type: HandType,
    val chain: Int = 1,
    val mainRank: Int,
    val wing: WingKind = WingKind.NONE,
) {
    val size: Int get() = cards.size

    /** True for any bomb family hand. */
    val isBomb: Boolean get() = type.isBomb

    /** True when this hand may be played on top of [previous] (null = free lead). */
    fun canBeat(previous: Hand?): Boolean = beats(this, previous)

    override fun toString(): String =
        "${type.label}(${cards.joinToString(" ")})"
}

/** Whether [hand] legally beats [previous]; a null [previous] means a free lead. */
fun beats(hand: Hand?, previous: Hand?): Boolean {
    if (hand == null) return false
    if (previous == null) return true
    if (hand.type == HandType.ROCKET) return previous.type != HandType.ROCKET
    if (previous.type == HandType.ROCKET) return false

    val newBomb = hand.type.isBomb
    val oldBomb = previous.type.isBomb
    if (newBomb && !oldBomb) return true
    if (!newBomb && oldBomb) return false
    if (newBomb && oldBomb) {
        // 天地癞子 bomb ladder: more cards wins, then soft < hard < pure bomb.
        if (hand.cards.size != previous.cards.size) {
            return hand.cards.size > previous.cards.size
        }
        if (hand.type.bombTier != previous.type.bombTier) {
            return hand.type.bombTier > previous.type.bombTier
        }
        return hand.mainRank > previous.mainRank
    }

    if (hand.type != previous.type) return false
    if (hand.chain != previous.chain || hand.wing != previous.wing) return false
    if (hand.cards.size != previous.cards.size) return false
    return hand.mainRank > previous.mainRank
}

/**
 * Recognises card combinations. Pure, allocation-light and deterministic.
 *
 * Rule set implemented (documented in `docs/rules.md`):
 * - 顺子: >=5 consecutive single ranks, 3..A, no 2 / jokers.
 * - 连对: >=3 consecutive pairs, 3..A.
 * - 飞机: >=2 consecutive triples, 3..A.
 * - 飞机带单: n consecutive triples + n arbitrary kickers.
 * - 飞机带对: n consecutive triples + n pairs (distinct ranks).
 * - 四带二: four of a kind + any two kickers.
 * - 四带两对: four of a kind + two distinct pairs.
 */
object HandParser {

    /** Parse [cards] in classic (no wild-card) mode. */
    fun parse(cards: List<Card>): Hand? = parseClassic(cards)

    /**
     * Parse [cards] allowing [laiziRanks] to act as wild cards.
     * When no wild rank is given this is exactly [parse].
     */
    fun parse(cards: List<Card>, laiziRanks: Set<Rank>): Hand? =
        if (laiziRanks.isEmpty()) parseClassic(cards) else LaiziHandParser.parse(cards, laiziRanks)

    /** Classic parser, also used by the laizi parser for wild-free selections. */
    internal fun parseClassic(cards: List<Card>): Hand? {
        if (cards.isEmpty()) return null
        val sorted = cards.sorted()
        val counts: Map<Rank, Int> = sorted.groupingBy { it.rank }.eachCount()
        val ranks = counts.keys.sortedBy { it.value }

        fun hand(
            type: HandType,
            chain: Int = 1,
            mainRank: Int = ranks.last().value,
            wing: WingKind = WingKind.NONE,
        ): Hand = Hand(sorted, type, chain, mainRank, wing)

        // 王炸: exactly the two distinct jokers.
        if (sorted.size == 2 && counts.size == 2 && counts.keys.all { it.isJoker }) {
            return hand(HandType.ROCKET, mainRank = Rank.BIG_JOKER.value)
        }

        if (counts.size == 1) {
            return when (sorted.size) {
                1 -> hand(HandType.SINGLE)
                2 -> hand(HandType.PAIR)
                3 -> hand(HandType.TRIPLE)
                4 -> hand(HandType.BOMB)
                else -> null
            }
        }

        // 三带一 / 三带二
        val tripleRanks = ranks.filter { counts[it] == 3 }
        if (sorted.size == 4 && tripleRanks.size == 1) {
            return hand(HandType.TRIPLE_ONE, mainRank = tripleRanks.single().value)
        }
        if (sorted.size == 5 && tripleRanks.size == 1) {
            val kicker = ranks.first { it != tripleRanks.single() }
            if (counts[kicker] == 2) {
                return hand(HandType.TRIPLE_TWO, mainRank = tripleRanks.single().value)
            }
        }

        // 顺子
        if (sorted.size >= 5 && counts.values.all { it == 1 } && isConsecutive(ranks) &&
            ranks.last().isChainable
        ) {
            return hand(HandType.STRAIGHT, chain = sorted.size)
        }

        // 连对
        if (sorted.size >= 6 && sorted.size % 2 == 0 && counts.values.all { it == 2 } &&
            isConsecutive(ranks) && ranks.last().isChainable
        ) {
            return hand(HandType.DOUBLE_STRAIGHT, chain = sorted.size / 2)
        }

        // 四带二 / 四带两对
        val fourRanks = ranks.filter { counts[it] == 4 }
        if (fourRanks.size == 1) {
            val four = fourRanks.single()
            if (sorted.size == 6) {
                return hand(HandType.FOUR_TWO, mainRank = four.value)
            }
            if (sorted.size == 8) {
                val rest = counts.filterKeys { it != four }
                if (rest.size == 2 && rest.values.all { it == 2 }) {
                    return hand(HandType.FOUR_TWO_PAIR, mainRank = four.value)
                }
            }
        }

        // 飞机 (pure) — detect before wings so 333444555666 is not read as wings.
        if (sorted.size >= 6 && sorted.size % 3 == 0 && counts.values.all { it == 3 } &&
            isConsecutive(ranks) && ranks.last().isChainable
        ) {
            return hand(HandType.AIRPLANE, chain = sorted.size / 3)
        }

        // 飞机带翅膀
        return parseAirplaneWithWings(sorted, counts) ?: null
    }

    private fun parseAirplaneWithWings(sorted: List<Card>, counts: Map<Rank, Int>): Hand? {
        val tripleCandidates = counts.keys
            .filter { (counts[it] ?: 0) >= 3 && it.isChainable }
            .sortedBy { it.value }
        if (tripleCandidates.size < 2) return null

        var best: Hand? = null
        for (start in tripleCandidates.indices) {
            for (end in tripleCandidates.lastIndex downTo start + 1) {
                val slice = tripleCandidates.subList(start, end + 1)
                if (!isConsecutive(slice)) continue
                val n = slice.size

                val used = buildList {
                    for (rank in slice) {
                        addAll(sorted.filter { it.rank == rank }.take(3))
                    }
                }
                val rest = sorted.toMutableList().apply { used.forEach { remove(it) } }
                val restCounts = rest.groupingBy { it.rank }.eachCount()
                val isPairWings = rest.size == 2 * n && restCounts.values.all { it == 2 }
                val isSingleWings = rest.size == n

                if (isPairWings || isSingleWings) {
                    val wing = if (isPairWings) WingKind.PAIR else WingKind.SINGLE
                    val candidate = Hand(
                        cards = sorted,
                        type = HandType.AIRPLANE_WINGS,
                        chain = n,
                        mainRank = slice.last().value,
                        wing = wing,
                    )
                    if (best == null || candidate.chain > best.chain) best = candidate
                }
            }
        }
        return best
    }

    private fun isConsecutive(ranks: List<Rank>): Boolean {
        if (ranks.isEmpty()) return false
        for (i in 1 until ranks.size) {
            if (ranks[i].value != ranks[i - 1].value + 1) return false
        }
        return true
    }
}

/** Convenience wrapper around [HandParser.parse]. */
fun parseHand(cards: List<Card>): Hand? = HandParser.parse(cards)

/** Convenience wrapper for laizi-aware parsing. */
fun parseHand(cards: List<Card>, laiziRanks: Set<Rank>): Hand? =
    HandParser.parse(cards, laiziRanks)

/** Group cards by rank, preserving no particular order. */
fun List<Card>.rankCounts(): Map<Rank, Int> = groupingBy { it.rank }.eachCount()
