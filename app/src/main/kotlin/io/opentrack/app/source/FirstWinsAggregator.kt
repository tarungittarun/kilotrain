package io.opentrack.app.source

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/**
 * ADR 0014: the FIRST-WINS aggregator. The owner's rule - "have at least 5 fast, reliable,
 * easy-connectable sources and display the first received info to the user".
 *
 * Every rung is a named, independently-cancellable attempt (6.3/3.8: nothing blocks the first
 * answer; a slow rung is cancelled the moment a faster one lands). The winner's label is shown on
 * screen, and the other rungs' outcomes are recorded so a failure says what was tried.
 */
class FirstWinsAggregator<T> {

    data class Rung<T>(val name: String, val attempt: suspend () -> T?)

    data class Outcome<T>(
        val value: T?,
        val winner: String?,
        val attempts: List<String>,
    )

    /**
     * Runs [rungs] in parallel and returns as soon as one produces a non-null answer. The rest are
     * cancelled. If all fail, the outcome carries every attempt's name so the UI can state them.
     */
    suspend fun race(rungs: List<Rung<T>>): Outcome<T> = coroutineScope {
        val attempts = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val deferred = rungs.map { rung -> rung to async { tryAttempt(rung, attempts) } }
        var winner: T? = null
        var winnerName: String? = null
        while (winner == null && deferred.any { it.second.isActive }) {
            select<Unit> {
                deferred.forEach { (rung, deferredValue) ->
                    deferredValue.onAwait { value ->
                        if (value != null && winner == null) {
                            winner = value
                            winnerName = rung.name
                        } else if (value == null) {
                            attempts.add("$rung: no answer")
                        }
                    }
                }
            }
        }
        deferred.forEach { (rung, d) ->
            if (winnerName != rung.name && d.isActive) {
                attempts.add("$rung: cancelled (a faster source answered)")
                d.cancel()
            }
        }
        Outcome(winner, winnerName, attempts.toList())
    }

    private suspend fun tryAttempt(rung: Rung<T>, attempts: java.util.Queue<String>): T? = try {
        rung.attempt()
    } catch (e: Exception) {
        attempts.add("${rung.name}: ${e.javaClass.simpleName}")
        null
    }
}
