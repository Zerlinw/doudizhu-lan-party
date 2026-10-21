package com.ddz.shared.ai

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.HandType
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.WingKind

/**
 * Generates legal plays for the AI (and the UI "提示" button).
 *
 * In 天地癞子 mode, `laiziRanks` is passed to the parser and to the extra
 * generators below, so wild cards can complete pairs, runs and bombs. The
 * generators are intentionally bounded (combination cap / one-ply patterns),
 * keeping a turn in the low milliseconds.
 */
object HandFinder {

    /** All legal hands that beat [last] using [hand]. Includes bombs / rocket. */
    fun beating(
        hand: List<Card>,
        last: Hand,
        laiziRanks: Set<Rank> = emptySet(),
        fullSearch: Boolean = true,
    ): List<Hand> {
        val result = mutableListOf<Hand>()
        val byRank = HandAnalyzer.byRank(hand)

        fun add(cards: List<Card>) {
            if (cards.isEmpty()) return
            val parsed = HandParser.parse(cards, laiziRanks) ?: return
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

            HandType.BOMB, HandType.SOFT_BOMB, HandType.PURE_LAIZI_BOMB,
            HandType.ROCKET -> Unit
        }

        // Natural hard bombs + rocket are legal follow-ups for every hand.
        byRank.forEach { (_, cards) ->
            if (cards.size == 4) add(cards)
        }
        val small = byRank[Rank.SMALL_JOKER]
        val big = byRank[Rank.BIG_JOKER]
        if (small != null && big != null) add(listOf(small.first(), big.first()))

        // Wild-card bombs (soft / pure / larger) for the laizi mode.
        if (fullSearch) {
            bombs(hand, laiziRanks).forEach { add(it.cards) }
        }

        if (laiziRanks.isNotEmpty() && hand.any { it.rank in laiziRanks }) {
            val wilds = hand.filter { it.rank in laiziRanks }.sorted()
            // Brute-force the small shapes; this reliably covers pairs, triples,
            // triple-with-kickers, four-with-kickers and short runs.
            if (fullSearch && !last.type.isBomb && last.cards.size in 1..6) {
                combinations(hand, last.cards.size, MAX_COMBINATIONS, ::add)
            }
            when (last.type) {
                HandType.STRAIGHT -> addWildRuns(hand, wilds, laiziRanks, last.chain, 1, ::add)
                HandType.DOUBLE_STRAIGHT ->
                    addWildRuns(hand, wilds, laiziRanks, last.chain, 2, ::add)
                HandType.AIRPLANE -> addWildRuns(hand, wilds, laiziRanks, last.chain, 3, ::add)
                HandType.AIRPLANE_WINGS -> addWildAirplaneWings(
                    hand = hand,
                    wilds = wilds,
                    laiziRanks = laiziRanks,
                    chain = last.chain,
                    wing = last.wing,
                    add = ::add,
                )
                else -> Unit
            }
        }

        return result.distinctBy { it.cards.map { c -> c.code }.sorted() }
    }

    /**
     * All bomb-family hands available in [hand] (hard, soft, pure, rocket).
     * Empty when [laiziRanks] is empty; natural bombs are generated by callers.
     */
    fun bombs(hand: List<Card>, laiziRanks: Set<Rank>): List<Hand> {
        if (laiziRanks.isEmpty()) return emptyList()
        val wilds = hand.filter { it.rank in laiziRanks }.sorted()
        if (wilds.isEmpty()) return emptyList()

        val naturals = hand.filter { it.rank !in laiziRanks }
        val naturalByRank = HandAnalyzer.byRank(naturals)
        val result = mutableListOf<Hand>()

        fun add(cards: List<Card>) {
            val parsed = HandParser.parse(cards, laiziRanks) ?: return
            if (parsed.type.isBomb) result += parsed
        }

        for ((_, cards) in naturalByRank) {
            if (cards.size >= 4) add(cards.take(4))
            for (naturalCount in 1..minOf(cards.size, 4)) {
                for (wildCount in 0..wilds.size) {
                    val size = naturalCount + wildCount
                    if (size < 4 || size > 12) continue
                    add(cards.take(naturalCount) + wilds.take(wildCount))
                }
            }
        }
        for (size in 4..wilds.size) {
            add(wilds.take(size))
        }
        val small = naturalByRank[Rank.SMALL_JOKER]
        val big = naturalByRank[Rank.BIG_JOKER]
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

    /** Fills missing cards in a run with wild cards (exact amount). */
    private fun addWildRuns(
        hand: List<Card>,
        wilds: List<Card>,
        laiziRanks: Set<Rank>,
        length: Int,
        perRank: Int,
        add: (List<Card>) -> Unit,
    ) {
        if (length < 1) return
        val naturalByRank = HandAnalyzer.byRank(hand.filter { it.rank !in laiziRanks })
        for (start in 3..(14 - length + 1)) {
            val ranks = (start until start + length).map { Rank.fromValue(it) }
            val group = mutableListOf<Card>()
            var missing = 0
            for (rank in ranks) {
                val available = naturalByRank[rank].orEmpty().take(perRank)
                group += available
                missing += perRank - available.size
            }
            if (missing in 0..wilds.size) {
                add(group + wilds.take(missing))
            }
        }
    }

    private fun addWildAirplaneWings(
        hand: List<Card>,
        wilds: List<Card>,
        laiziRanks: Set<Rank>,
        chain: Int,
        wing: WingKind,
        add: (List<Card>) -> Unit,
    ) {
        if (wing != WingKind.SINGLE) return
        val naturalByRank = HandAnalyzer.byRank(hand.filter { it.rank !in laiziRanks })
        for (start in 3..(14 - chain + 1)) {
            val ranks = (start until start + chain).map { Rank.fromValue(it) }
            val triples = mutableListOf<Card>()
            var missing = 0
            for (rank in ranks) {
                val available = naturalByRank[rank].orEmpty().take(3)
                triples += available
                missing += 3 - available.size
            }
            if (missing > wilds.size) continue
            val usedWilds = wilds.take(missing)
            val used = (triples + usedWilds).map { it.code }.toSet()
            val wings = hand.filter { it.code !in used }.sorted().take(chain)
            if (wings.size == chain) add(triples + usedWilds + wings)
        }
    }

    /** Bounded depth-first enumeration of [size]-card subsets. */
    private fun combinations(
        hand: List<Card>,
        size: Int,
        limit: Int,
        action: (List<Card>) -> Unit,
    ) {
        val cards = hand.sorted()
        if (size < 1 || size > cards.size) return
        val picked = ArrayList<Card>(size)
        var emitted = 0

        fun walk(start: Int) {
            if (emitted >= limit) return
            if (picked.size == size) {
                action(picked.toList())
                emitted += 1
                return
            }
            val remaining = size - picked.size
            for (index in start..(cards.size - remaining)) {
                picked.add(cards[index])
                walk(index + 1)
                picked.removeAt(picked.lastIndex)
                if (emitted >= limit) return
            }
        }

        walk(0)
    }

    private const val MAX_COMBINATIONS = 20_000
}
