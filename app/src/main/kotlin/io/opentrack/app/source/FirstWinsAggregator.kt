package io.opentrack.app.source

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * ADR 0014: the FIRST-WINS aggregator. The owner's rule - "have at least 5 fast, reliable,
 * easy-connectable sources and display the first received info to the user".
 *
 * Rungs run concurrently; the first to produce a non-null answer wins and the rest are cancelled
 * (3.8: nothing blocks the first answer). Completion order is observed by polling the deferreds,
 * which keeps the semantics deterministic for both fast in-process sources and slow network ones.
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

        var winner: T? = null
        var winnerName: String? = null
        while (winner == null) {
            val landed = started.firstOrNull { (_, deferred) ->
                deferred.isCompleted && completedValue(deferred) != null
            }
            if (landed != null) {
                winner = completedValue(landed.second)
                winnerName = landed.first.name
                break
            }
            if (started.all { (_, deferred) -> deferred.isCompleted }) break
            delay(POLL_MS) // observe completion order without blocking the first answer
        }

        started.forEach { (rung, deferred) ->
            if (rung.name != winnerName) {
                if (deferred.isActive) {
                    attempts.add("${rung.name}: cancelled (a faster source answered)")
                    deferred.cancel()
                } else {
                    val value = completedValue(deferred)
                    if (value == null && attempts.none { it.startsWith(rung.name + ":") }) {
                        attempts.add("${rung.name}: no answer")
                    }
                }
            }
        }
        Outcome(winner, winnerName, attempts.toList())
    }

    private fun completedValue(deferred: Deferred<T?>): T? =
        if (deferred.isCompleted) runCatching { deferred.getCompleted() }.getOrNull() else null

    private suspend fun tryAttempt(rung: Rung<T>, attempts: java.util.Queue<String>): T? = try {
        rung.attempt()
    } catch (e: Exception) {
        attempts.add("${rung.name}: ${e.javaClass.simpleName}")
        null
    }

    private companion object {
        const val POLL_MS = 20L
    }
}
