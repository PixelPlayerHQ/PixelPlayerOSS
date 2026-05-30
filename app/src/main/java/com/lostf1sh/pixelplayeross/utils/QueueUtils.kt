package com.lostf1sh.pixelplayeross.utils

import com.lostf1sh.pixelplayeross.data.model.Song
import kotlinx.coroutines.yield
import kotlin.random.Random

object QueueUtils {

    /**
     * Batch size for yielding during shuffle of very large collections.
     * Every [SHUFFLE_YIELD_BATCH] swaps we yield to avoid blocking the caller coroutine.
     */
    private const val SHUFFLE_YIELD_BATCH = 512

    fun <T> fisherYatesCopy(source: List<T>, random: Random = Random.Default): List<T> {
        if (source.size <= 1) return source.toList()
        val mutable = source.toMutableList()
        for (i in mutable.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            if (i != j) {
                val tmp = mutable[i]
                mutable[i] = mutable[j]
                mutable[j] = tmp
            }
        }
        return mutable
    }

    private fun generateShuffleOrder(size: Int, anchorIndex: Int, random: Random = Random.Default): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
        }

        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
        }

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else pool[poolIndex++]
        }
        return order
    }

    /**
     * Suspendable version of [generateShuffleOrder] that yields periodically for large queues.
     * This prevents ANR when shuffling 10,000+ songs by cooperating with the coroutine dispatcher.
     */
    private suspend fun generateShuffleOrderSuspending(
        size: Int,
        anchorIndex: Int,
        random: Random = Random.Default
    ): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        var workSinceYield = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }

        // Fisher-Yates with periodic yield for very large pools
        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else pool[poolIndex++]
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        return order
    }

    fun buildAnchoredShuffleQueue(
        currentQueue: List<Song>,
        anchorIndex: Int,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        val order = generateShuffleOrder(currentQueue.size, anchorIndex, random)
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }

    /**
     * Suspendable shuffle that yields periodically for large queues (10,000+).
     * Maintains O(n) Fisher-Yates complexity with uniform randomness.
     */
    suspend fun buildAnchoredShuffleQueueSuspending(
        currentQueue: List<Song>,
        anchorIndex: Int,
        startAtZero: Boolean = false,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        
        val order = if (startAtZero) {
             generateShuffleOrderStartAtZero(currentQueue.size, anchorIndex, random)
        } else {
             generateShuffleOrderSuspending(currentQueue.size, anchorIndex, random)
        }
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }

    private suspend fun generateShuffleOrderStartAtZero(
        size: Int,
        anchorIndex: Int,
        random: Random = Random.Default
    ): IntArray {
        if (size <= 1) return IntArray(size) { it }
        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        var workSinceYield = 0
        
        // Fill pool with everything EXCEPT the anchor
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        // Fisher-Yates shuffle the pool
        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
            workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        // Construct final order: Anchor is ALWAYS at 0, followed by shuffled pool
        val order = IntArray(size)
        order[0] = clampedAnchor
        
        for (i in 0 until pool.size) {
            order[i + 1] = pool[i]
             workSinceYield++
            if (workSinceYield >= SHUFFLE_YIELD_BATCH) {
                workSinceYield = 0
                yield()
            }
        }
        
        return order
    }
}
