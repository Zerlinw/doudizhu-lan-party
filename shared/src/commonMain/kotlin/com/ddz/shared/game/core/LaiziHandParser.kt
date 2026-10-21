package com.ddz.shared.game.core

/**
 * 天地癞子 hand recognition.
 *
 * Wild cards (cards whose printed rank is the 天/地癞子 rank) may represent any
 * ordinary rank 3..2, but never a joker. The parser tries every reasonable
 * assignment and returns the strongest deterministic interpretation:
 *
 * - any all-wild or natural+wild same-rank group of >=4 cards is a bomb;
 * - bombs are ordered by size, then 软炸 < 硬炸 < 纯癞子炸;
 * - otherwise the usual 斗地主 shapes are checked with wild-card filling.
 *
 * This is a pure function; it does not mutate the input cards.
 */
internal object LaiziHandParser {

    fun parse(cards: List<Card>, laiziRanks: Set<Rank>): Hand? {
        if (cards.isEmpty()) return null
        val wilds = cards.filter { it.rank in laiziRanks }.sorted()
        if (wilds.isEmpty()) return HandParser.parseClassic(cards)

        val sorted = cards.sorted()
        val naturals = sorted.filter { it.rank !in laiziRanks }
        val wildCount = wilds.size
        val counts = naturals.groupingBy { it.rank }.eachCount()
        val n = sorted.size

        fun hand(
            type: HandType,
            chain: Int = 1,
            mainRank: Int,
            wing: WingKind = WingKind.NONE,
        ): Hand = Hand(sorted, type, chain, mainRank, wing)

        // 王炸: exactly both jokers, no wild cards involved.
        if (n == 2 && naturals.size == 2 && naturals.all { it.isJoker } &&
            naturals[0].rank != naturals[1].rank
        ) {
            return hand(HandType.ROCKET, mainRank = Rank.BIG_JOKER.value)
        }

        bombShape(naturals, wilds, n)?.let { shape ->
            return hand(shape.type, mainRank = shape.mainRank)
        }

        when (n) {
            1 -> {
                val rank = naturals.firstOrNull()?.rank
                return hand(HandType.SINGLE, mainRank = rank?.value ?: Rank.TWO.value)
            }

            2 -> {
                val pair = pairRank(naturals, wildCount) ?: return null
                return hand(HandType.PAIR, mainRank = pair.value)
            }

            3 -> {
                val triple = pureTripleRank(naturals, wildCount) ?: return null
                return hand(HandType.TRIPLE, mainRank = triple.value)
            }
        }

        if (n == 4) {
            tripleWithKicker(naturals, wilds, wildCount, pairKicker = false)?.let { rank ->
                return hand(HandType.TRIPLE_ONE, mainRank = rank.value)
            }
            return null
        }
        if (n == 5) {
            tripleWithKicker(naturals, wilds, wildCount, pairKicker = true)?.let { rank ->
                return hand(HandType.TRIPLE_TWO, mainRank = rank.value)
            }
        }

        straight(naturals, wildCount, n)?.let { top ->
            return hand(HandType.STRAIGHT, chain = n, mainRank = top)
        }
        doubleStraight(naturals, wildCount, n)?.let { top ->
            return hand(HandType.DOUBLE_STRAIGHT, chain = n / 2, mainRank = top)
        }

        if (n == 6) {
            fourWithKickers(naturals, wilds, wildCount, pairWings = false)?.let { rank ->
                return hand(HandType.FOUR_TWO, mainRank = rank.value)
            }
        }
        if (n == 8) {
            fourWithKickers(naturals, wilds, wildCount, pairWings = true)?.let { rank ->
                return hand(HandType.FOUR_TWO_PAIR, mainRank = rank.value)
            }
        }

        airplane(naturals, wildCount, n)?.let { top ->
            return hand(HandType.AIRPLANE, chain = n / 3, mainRank = top)
        }

        airplaneWings(naturals, wildCount, n)?.let { (top, chain, wing) ->
            return hand(HandType.AIRPLANE_WINGS, chain = chain, mainRank = top, wing = wing)
        }

        return null
    }

    private data class BombShape(val type: HandType, val mainRank: Int)

    private fun bombShape(
        naturals: List<Card>,
        wilds: List<Card>,
        n: Int,
    ): BombShape? {
        if (n < 4) return null
        if (naturals.any { it.isJoker }) return null
        val ranks = naturals.map { it.rank }.distinct()
        if (ranks.size > 1) return null
        return when {
            naturals.isEmpty() -> BombShape(
                HandType.PURE_LAIZI_BOMB,
                wilds.maxOf { it.rank.value },
            )

            wilds.isEmpty() -> {
                if (n == 4) BombShape(HandType.BOMB, ranks.single().value) else null
            }

            else -> BombShape(HandType.SOFT_BOMB, ranks.single().value)
        }
    }

    /** Pair rank if all cards can form a pair; null otherwise. */
    private fun pairRank(naturals: List<Card>, wilds: Int): Rank? {
        if (naturals.isEmpty()) return if (wilds == 2) Rank.TWO else null
        if (naturals.any { it.isJoker }) return null
        val ranks = naturals.map { it.rank }.distinct()
        return if (ranks.size == 1 && naturals.size + wilds == 2) ranks.single() else null
    }

    /** Triple rank if all cards can form one triple; wilds fill the rest. */
    private fun pureTripleRank(naturals: List<Card>, wilds: Int): Rank? {
        if (naturals.isEmpty()) return if (wilds == 3) Rank.TWO else null
        if (naturals.any { it.isJoker }) return null
        val ranks = naturals.map { it.rank }.distinct()
        if (ranks.size != 1) return null
        val count = naturals.size
        return if (count + wilds == 3) ranks.single() else null
    }

    /**
     * Find a triple and a 1-card (single) or 2-card (pair) kicker.
     * Returns the triple rank, preferring the highest possible rank.
     */
    private fun tripleWithKicker(
        naturals: List<Card>,
        wilds: List<Card>,
        wildCount: Int,
        pairKicker: Boolean,
    ): Rank? {
        val counts = naturals.groupingBy { it.rank }.eachCount()
        val candidates = mutableListOf<Pair<Rank, Int>>() // rank to wildcards needed
        for ((rank, count) in counts) {
            if (rank.isJoker) continue
            if (count > 3) continue
            val need = 3 - count
            if (need <= wildCount) candidates += rank to need
        }
        if (wildCount >= 3) candidates += Rank.TWO to 3

        for ((rank, need) in candidates.sortedByDescending { it.first.value }) {
            if (rank.isJoker) continue
            val usedNaturals = naturals.filter { it.rank == rank }.take(3)
            val remWild = wildCount - need
            val remNaturals = naturals.filterNot { it in usedNaturals }
            val remCount = remNaturals.size + remWild
            if (pairKicker) {
                if (remCount == 2 && canFormPair(remNaturals, remWild)) return rank
            } else {
                if (remCount == 1) return rank
            }
        }
        return null
    }

    private fun canFormPair(naturals: List<Card>, wilds: Int): Boolean {
        if (naturals.size + wilds != 2) return false
        if (naturals.isEmpty()) return wilds == 2
        if (naturals.any { it.isJoker }) return false
        if (naturals.size == 1) return wilds == 1
        return naturals[0].rank == naturals[1].rank
    }

    private fun canFormPairs(
        counts: Map<Rank, Int>,
        wilds: Int,
        pairs: Int,
    ): Boolean {
        if (counts.keys.any { it.isJoker }) return false
        if (counts.values.any { it > 2 }) return false
        val singles = counts.values.count { it == 1 }
        val existingPairs = counts.values.count { it == 2 }
        if (wilds < singles) return false
        val remainingWilds = wilds - singles
        if (remainingWilds % 2 != 0) return false
        return existingPairs + singles + remainingWilds / 2 == pairs
    }

    private fun fourWithKickers(
        naturals: List<Card>,
        wilds: List<Card>,
        wildCount: Int,
        pairWings: Boolean,
    ): Rank? {
        val counts = naturals.groupingBy { it.rank }.eachCount()
        val candidates = mutableListOf<Pair<Rank, Int>>()
        for ((rank, count) in counts) {
            if (rank.isJoker) continue
            if (count > 4) continue
            val need = 4 - count
            if (need <= wildCount) candidates += rank to need
        }
        if (wildCount >= 4) candidates += Rank.TWO to 4

        for ((rank, need) in candidates.sortedByDescending { it.first.value }) {
            val used = naturals.filter { it.rank == rank }.take(4)
            val remWild = wildCount - need
            val remNaturals = naturals.filterNot { it in used }
            if (pairWings) {
                val wings = remNaturals.groupingBy { it.rank }.eachCount()
                if (remNaturals.size + remWild == 4 && canFormPairs(wings, remWild, 2)) return rank
            } else {
                if (remNaturals.size + remWild == 2) return rank
            }
        }
        return null
    }

    private fun straight(naturals: List<Card>, wilds: Int, n: Int): Int? {
        if (n < 5 || n > 12) return null
        if (naturals.any { it.isJoker }) return null
        val counts = naturals.groupingBy { it.rank }.eachCount()
        var best: Int? = null
        for (start in 3..(14 - n + 1)) {
            val ranks = (start until start + n).map { Rank.fromValue(it) }
            if (counts.keys.any { it !in ranks }) continue
            if (counts.values.any { it > 1 }) continue
            val missing = ranks.count { (counts[it] ?: 0) == 0 }
            if (missing != wilds) continue
            best = ranks.last().value
        }
        return best
    }

    private fun doubleStraight(naturals: List<Card>, wilds: Int, n: Int): Int? {
        if (n < 6 || n % 2 != 0) return null
        val length = n / 2
        if (naturals.any { it.isJoker }) return null
        val counts = naturals.groupingBy { it.rank }.eachCount()
        if (counts.values.any { it > 2 }) return null
        var best: Int? = null
        for (start in 3..(14 - length + 1)) {
            val ranks = (start until start + length).map { Rank.fromValue(it) }
            if (counts.keys.any { it !in ranks }) continue
            val missing = ranks.sumOf { 2 - (counts[it] ?: 0) }
            if (missing != wilds) continue
            best = ranks.last().value
        }
        return best
    }

    private fun airplane(naturals: List<Card>, wilds: Int, n: Int): Int? {
        if (n < 6 || n % 3 != 0) return null
        val length = n / 3
        if (naturals.any { it.isJoker }) return null
        val counts = naturals.groupingBy { it.rank }.eachCount()
        if (counts.values.any { it > 3 }) return null
        var best: Int? = null
        for (start in 3..(14 - length + 1)) {
            val ranks = (start until start + length).map { Rank.fromValue(it) }
            if (counts.keys.any { it !in ranks }) continue
            val missing = ranks.sumOf { 3 - (counts[it] ?: 0) }
            if (missing != wilds) continue
            best = ranks.last().value
        }
        return best
    }

    private data class Wings(val top: Int, val chain: Int, val wing: WingKind)

    private fun airplaneWings(naturals: List<Card>, wilds: Int, n: Int): Wings? {
        if (n < 8) return null
        val singleChain = if (n % 4 == 0) n / 4 else null
        val pairChain = if (n % 5 == 0) n / 5 else null
        if (singleChain == null && pairChain == null) return null

        fun tryWing(chain: Int, wing: WingKind): Wings? {
            if (chain < 2) return null
            val counts = naturals.groupingBy { it.rank }.eachCount()
            var best: Wings? = null
            for (start in 3..(14 - chain + 1)) {
                val ranks = (start until start + chain).map { Rank.fromValue(it) }
                var missing = 0
                val used = mutableMapOf<Rank, Int>()
                for (rank in ranks) {
                    val c = counts[rank] ?: 0
                    val use = minOf(c, 3)
                    used[rank] = use
                    missing += 3 - use
                }
                if (missing > wilds) continue
                val remWild = wilds - missing
                val outside = counts.toMutableMap()
                for ((rank, use) in used) {
                    val left = (outside[rank] ?: 0) - use
                    if (left <= 0) outside.remove(rank) else outside[rank] = left
                }
                val outsideCount = outside.values.sum()
                if (outside.keys.any { it.isJoker } && wing == WingKind.PAIR) continue
                val valid = when (wing) {
                    WingKind.SINGLE -> outsideCount + remWild == chain
                    WingKind.PAIR -> canFormPairs(outside, remWild, chain)
                    WingKind.NONE -> false
                }
                if (valid) {
                    best = Wings(ranks.last().value, chain, wing)
                    break
                }
            }
            return best
        }

        if (singleChain != null) tryWing(singleChain, WingKind.SINGLE)?.let { return it }
        if (pairChain != null) tryWing(pairChain, WingKind.PAIR)?.let { return it }
        return null
    }
}
