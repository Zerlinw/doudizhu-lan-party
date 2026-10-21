package com.ddz.shared.game.core

/**
 * Small deterministic PRNG (64-bit LCG). Using our own generator keeps dealing
 * reproducible across JVM/Android/Native and makes tests deterministic.
 */
class DeterministicRandom(seed: Long) {
    private var state: Long = if (seed == 0L) 0x2545F4914F6CDD1DL else seed

    fun nextLong(): Long {
        state = state * 6364136223846793005L + 1442695040888963407L
        return state
    }

    /** Uniform-ish integer in `0 until bound`. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound).toInt()
    }
}

/** Result of dealing a round. */
data class DealResult(
    val hands: List<List<Card>>,
    val bottomCards: List<Card>,
) {
    init {
        require(hands.size == 3) { "Dou Dizhu needs exactly three hands" }
    }
}

/** Build a fresh, deterministically shuffled 54-card deck. */
fun shuffledDeck(seed: Long): List<Card> {
    val deck = Card.deck().toMutableList()
    val random = DeterministicRandom(seed)
    for (i in deck.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = deck[i]
        deck[i] = deck[j]
        deck[j] = tmp
    }
    return deck
}

/** Deal 17 / 17 / 17 + 3 bottom cards. */
fun dealCards(seed: Long): DealResult {
    val deck = shuffledDeck(seed)
    val hands = (0..2).map { seat ->
        deck.subList(seat * 17, seat * 17 + 17).sorted()
    }
    return DealResult(hands = hands, bottomCards = deck.subList(51, 54).sorted())
}

/**
 * Deterministically pick a 天/地癞子 rank. Jokers are never wild.
 * [exclude] prevents the 地癞子 from reusing the 天癞子 rank.
 */
fun pickLaiziRank(seed: Long, exclude: Set<Rank> = emptySet()): Rank {
    val candidates = Rank.normal().filterNot { it in exclude }
    require(candidates.isNotEmpty()) { "No laizi rank left after excluding $exclude" }
    val random = DeterministicRandom(seed xor 0x5DEECE66DL)
    return candidates[random.nextInt(candidates.size)]
}
