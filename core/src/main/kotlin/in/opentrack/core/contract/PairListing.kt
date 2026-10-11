package in.opentrack.core.contract

/** Typed source failures (6.3) - never a bare string, never silence. */
enum class SourceFailure {
    OFFLINE, TIMEOUT, HTTP, EMPTY, PARSE, SHAPE_UNKNOWN
}

/**
 * Trains between a station pair. An EMPTY listing and a FAILURE are different values (3.3):
 * an empty list must never be produced by a failure.
 */
data class PairListing(
    val fromStation: String,
    val toStation: String,
    val rows: List<TrainIdentity>,
    val provenance: Provenance,
    val note: String? = null,
) {
    companion object {
        fun empty(
            fromStation: String,
            toStation: String,
            provenance: Provenance,
        ) = PairListing(fromStation, toStation, emptyList(), provenance)

        fun failed(
            fromStation: String,
            toStation: String,
            failure: SourceFailure,
            detail: String,
        ) = Failure(fromStation, toStation, failure, detail)
    }

    data class Failure(
        val fromStation: String,
        val toStation: String,
        val failure: SourceFailure,
        val detail: String,
    )
}

/** The result type that keeps EMPTY and COULD-NOT-LOOK apart all the way to the UI (3.3). */
sealed interface PairLookup {
    data class Answered(val listing: PairListing) : PairLookup
    data class CouldNotLook(val failure: PairListing.Failure) : PairLookup
}
