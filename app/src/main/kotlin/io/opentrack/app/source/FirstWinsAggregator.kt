package io.opentrack.app.source

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * ADR 0014: the FIRST-WINS aggregator. The owner's rule - "have at least 5 fast, reliable,
 * easy-connectable sources and display the first received info to the user".
 *
 * Every rung is a named, independently-cancellable attempt (6.3/3.8: nothing blocks the first
 * answer; a slow rung is cancelled the moment a faster one lands). Fast rungs that finish before
 * the race loop starts are honoured in rung order, so a synchronous answer is still "first".
 */
class FirstWinsAggregator<T> {

    data class Rung<T>(val name: String, val attempt: suspend () -> T?)

    data class Outcome<T>(
        val value: T?,
        val winner: String?,
        val attempts: List<String>,
    )

    suspend fun race(rungs: List<Rung<T>>): Outcome<T> = coroutineScope {
        if (rungs.isEmpty()) return@coroutineScope Outcome(null, null, emptyList())
        val attempts = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val started = rungs.map { rung -> rung to async { tryAttempt(rung, attempts) } }

        // 1) fast path: rungs that already answered (fakes, caches, instant sources), in order
        for ((rung, deferred) in started) {
            if (deferred.isCompleted) {
                val value = completedValue(deferred)
                if (value != null) {
                    cancelRest(started, rung.name, attempts)
                    return@coroutineScope Outcome(value, rung.name, attempts.toList())
                }
            }
        }

        // 2) race the pending rungs: the first to land wins, the rest are cancelled
        var winner: T? = null
        var winnerName: String? = null
        while (winner == null) {
            val pending = started.filter { it.second.isActive }
            if (pending.isEmpty()) break
            select<Unit> {
                pending.forEach { (rung, deferred) ->
                    deferred.onAwait { value ->
                        if (value != null) {
                            winner = value
                            winnerName = rung.name
                        } else {
                            attempts.add("${rung.name}: no answer")
                        }
                    }
                }
            }
        }
        cancelRest(started, winnerName, attempts)
        Outcome(winner, winnerName, attempts.toList())
    }

    private fun cancelRest(
        started: List<Pair<Rung<T>, Deferred<T?>>>,
        winnerName: String?,
        attempts: java.util.Queue<String>,
    ) {
        started.forEach { (rung, deferred) ->
            if (rung.name != winnerName && deferred.isActive) {
                attempts.add("${rung.name}: cancelled (a faster source answered)")
                deferred.cancel()
            }
        }
    }

    private fun completedValue(deferred: Deferred<T?>): T? =
        if (deferred.isCompleted) runCatching { deferred.getCompleted() }.getOrNull() else null

    private suspend fun tryAttempt(rung: Rung<T>, attempts: java.util.Queue<String>): T? = try {
        rung.attempt()
    } catch (e: Exception) {
        attempts.add("${rung.name}: ${e.javaClass.simpleName}")
        null
    }
}
