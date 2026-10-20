package com.ddz.shared.ai

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.Rank

/**
 * Splits a hand into parseable groups, cheapest-first. Used by the heuristic AI
 * to decide what to lead and to avoid breaking bombs.
 *
 * This is a greedy decomposition, not an optimal search: it is intentionally
 * cheap (O(n log n)-ish) so a turn stays well under 20 ms on low-end phones.
 */
object HandAnalyzer {

    /** Cards grouped by rank in ascending rank order. */
    fun byRank(hand: List<Card>): Map<Rank, List<Card>> =
        hand.groupBy { it.rank }
            .entries
            .sortedBy { it.key.value }
            .associate { it.key to it.value }

    /** Decompose into valid groups: rocket, bombs, airplanes, straights, ... singles. */
    fun decompose(hand: List<Card>): List<List<Card>> {
        if (hand.isEmpty()) return emptyList()
        val pool: MutableMap<Rank, MutableList<Card>> = hand.groupBy { it.rank }
            .mapValuesTo(mutableMapOf()) { (_, cards) -> cards.sortedBy { it.suit.ordinal }.toMutableList() }
        val groups = mutableListOf<List<Card>>()

        fun count(rank: Rank): Int = pool[rank]?.size ?: 0

        fun take(rank: Rank, n: Int): List<Card> {
            val list = pool[rank] ?: return emptyList()
            val taken = list.take(n)
            repeat(taken.size) { list.removeAt(0) }
            if (list.isEmpty()) pool.remove(rank)
            return taken
        }

        fun ranksAsc(): List<Rank> = pool.keys.sortedBy { it.value }

        fun longestRun(minCount: Int, minLength: Int): List<Rank>? {
            val eligible = ranksAsc().filter { count(it) >= minCount && it.isChainable }
            var best = mutableListOf<Rank>()
            var current = mutableListOf<Rank>()
            for (rank in eligible) {
                if (current.isEmpty() || rank.value == current.last().value + 1) {
                    current.add(rank)
                } else {
                    if (current.size > best.size) best = current
                    current = mutableListOf(rank)
                }
            }
            if (current.size > best.size) best = current
            return if (best.size >= minLength) best else null
        }

        fun takeRun(run: List<Rank>, perRank: Int): List<Card> =
            run.flatMap { rank -> take(rank, perRank) }

        // 王炸
        if (count(Rank.SMALL_JOKER) > 0 && count(Rank.BIG_JOKER) > 0) {
            groups += take(Rank.SMALL_JOKER, 1) + take(Rank.BIG_JOKER, 1)
        }
        // 炸弹
        for (rank in ranksAsc()) {
            if (count(rank) == 4) groups += take(rank, 4)
        }
        // 飞机
        while (true) {
            val run = longestRun(minCount = 3, minLength = 2) ?: break
            groups += takeRun(run, 3)
        }
        // 连对
        while (true) {
            val run = longestRun(minCount = 2, minLength = 3) ?: break
            groups += takeRun(run, 2)
        }
        // 顺子
        while (true) {
            val run = longestRun(minCount = 1, minLength = 5) ?: break
            groups += takeRun(run, 1)
        }
        // 三张 / 对子 / 单张
        for (rank in ranksAsc()) {
            if (count(rank) >= 3) groups += take(rank, 3)
        }
        for (rank in ranksAsc()) {
            if (count(rank) >= 2) groups += take(rank, 2)
        }
        for (rank in ranksAsc()) {
            while (count(rank) > 0) groups += take(rank, 1)
        }
        return groups
    }

    /** Groups that can be played as a legal lead, including the whole hand. */
    fun leadCandidates(hand: List<Card>): List<Hand> {
        val result = mutableListOf<Hand>()
        HandParser.parse(hand)?.let { result += it }
        for (group in decompose(hand)) {
            HandParser.parse(group)?.let { result += it }
        }
        return result.distinctBy { it.cards.map { c -> c.code }.sorted() }
    }
}
