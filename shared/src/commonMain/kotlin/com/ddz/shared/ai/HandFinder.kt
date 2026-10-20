package com.ddz.shared.ai

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.HandType
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.WingKind

/** Generates legal plays for the AI (and for the UI "提示" button). */
object HandFinder {

    /** All legal hands that beat [last] using [hand]. Includes bombs / rocket. */
    fun beating(hand: List<Card>, last: Hand): List<Hand> {
        val result = mutableListOf<Hand>()
        val byRank = HandAnalyzer.byRank(hand)

        fun add(cards: List<Card>) {
            if (cards.isEmpty()) return
            val parsed = HandParser.parse(cards) ?: return
            if (parsed.canBeat(last)) result += parsed
        }

        fun lowest(used: Set<Int>, n: Int): List<Card> =
            hand.filter { it.code !in used }.sorted().take(n)

        fun lowestPairs(used: Set<Int>, pairs: Int): List<Card> {
            if (pairs <= 0) return emptyList()
            val available = hand.filter { it.code !in used }
                .groupBy { it.rank }
                .entries
                .sortedBy { it.key.value }
                .filter { it.value.size >= 2 }
            if (available.size < pairs) return emptyList()
            return available.take(pairs).flatMap { it.value.take(2) }
        }

        when (last.type) {
            HandType.SINGLE -> byRank.forEach { (rank, cards) ->
                if (rank.value > last.mainRank) add(listOf(cards.first()))
            }

            HandType.PAIR -> byRank.forEach { (rank, cards) ->
                if (rank.value > last.mainRank && cards.size >= 2) add(cards.take(2))
            }

            HandType.TRIPLE -> byRank.forEach { (rank, cards) ->
                if (rank.value > last.mainRank && cards.size >= 3) add(cards.take(3))
            }

            HandType.TRIPLE_ONE -> byRank.forEach { (rank, cards) ->
                if (rank.value > last.mainRank && cards.size >= 3) {
                    val triple = cards.take(3)
                    add(triple + lowest(triple.map { it.code }.toSet(), 1))
                }
            }

            HandType.TRIPLE_TWO -> byRank.forEach { (rank, cards) ->
                if (rank.value > last.mainRank && cards.size >= 3) {
                    val triple = cards.take(3)
                    add(triple + lowestPairs(triple.map { it.code }.toSet(), 1))
                }
            }

            HandType.STRAIGHT -> addRuns(byRank, last.chain, perRank = 1, add = ::add)

            HandType.DOUBLE_STRAIGHT -> addRuns(byRank, last.chain, perRank = 2, add = ::add)

            HandType.AIRPLANE -> addRuns(byRank, last.chain, perRank = 3, add = ::add)

            HandType.AIRPLANE_WINGS -> {
                for (start in 3..(14 - last.chain + 1)) {
                    val ranks = (start until start + last.chain).map { Rank.fromValue(it) }
                    val tripleCards = ranks.map { rank -> byRank[rank]?.take(3).orEmpty() }
                    if (tripleCards.any { it.size < 3 }) continue
                    val triples = tripleCards.flatten()
                    val used = triples.map { it.code }.toSet()
                    val wings = if (last.wing == WingKind.PAIR) {
                        lowestPairs(used, last.chain)
                    } else {
                        lowest(used, last.chain)
                    }
                    add(triples + wings)
                }
            }

            HandType.FOUR_TWO -> byRank.forEach { (_, cards) ->
                if (cards.size == 4) {
                    add(cards + lowest(cards.map { it.code }.toSet(), 2))
                }
            }

            HandType.FOUR_TWO_PAIR -> byRank.forEach { (_, cards) ->
                if (cards.size == 4) {
                    add(cards + lowestPairs(cards.map { it.code }.toSet(), 2))
                }
            }

            HandType.BOMB, HandType.ROCKET -> Unit
        }

        // Bombs and rocket are legal follow-ups for every non-rocket hand.
        byRank.forEach { (_, cards) ->
            if (cards.size == 4) add(cards)
        }
        val small = byRank[Rank.SMALL_JOKER]
        val big = byRank[Rank.BIG_JOKER]
        if (small != null && big != null) add(listOf(small.first(), big.first()))

        return result.distinctBy { it.cards.map { c -> c.code }.sorted() }
    }

    private fun addRuns(
        byRank: Map<Rank, List<Card>>,
        length: Int,
        perRank: Int,
        add: (List<Card>) -> Unit,
    ) {
        if (length < 1) return
        for (start in 3..(14 - length + 1)) {
            val ranks = (start until start + length).map { Rank.fromValue(it) }
            val group = mutableListOf<Card>()
            var complete = true
            for (rank in ranks) {
                val cards = byRank[rank]?.take(perRank).orEmpty()
                if (cards.size < perRank) {
                    complete = false
                    break
                }
                group += cards
            }
            if (complete) add(group)
        }
    }
}
