package io.opentrack.core.contract

/**
 * Train identity as the railway currently uses it (2.3). Number and name are NEVER cleaned up
 * in a way that alters identity (3.4).
 */
data class TrainIdentity(
    val number: String,
    val name: String,
    val fromStation: String,
    val toStation: String,
    val provenance: Provenance,
) {
    /** Two rows agree on identity when number, name and endpoints agree. */
    fun disagreesWith(other: TrainIdentity): Boolean =
        number == other.number && (name != other.name
            || fromStation != other.fromStation
            || toStation != other.toStation)

    /** Same number, different train (the reverse-keyed/different-train failure mode, 10.3). */
    fun conflictsWith(other: TrainIdentity): Boolean =
        number == other.number && (fromStation != other.fromStation || toStation != other.toStation)
}
