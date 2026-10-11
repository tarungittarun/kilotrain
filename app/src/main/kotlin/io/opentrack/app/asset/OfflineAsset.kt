package in.opentrack.app.asset

import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity
import org.json.JSONObject

/** One row of the labelled cold-start fallback (ADR 0007). */
data class AssetTrain(
    val number: String,
    val name: String,
    val fromStation: String,
    val toStation: String,
    val departure: String,
    val arrival: String,
    val type: String,
    val zone: String,
) {
    fun toIdentity(): TrainIdentity = TrainIdentity(
        number = number,
        name = name,
        fromStation = fromStation,
        toStation = toStation,
        provenance = Provenance(
            kind = ProvenanceKind.TIMETABLE,
            source = "offline fallback",
            fetchedAtMillis = 0, // least trusted until a real stamp exists (6.2)
        ),
    )
}

/**
 * The shipped offline asset (6.7: <= 6 MB gzipped). Schema has its own version and a mismatch
 * DEGRADES with a stated message, never a crash (11.3).
 */
data class OfflineAsset(
    val schemaVersion: Int,
    val trains: List<AssetTrain>,
    val schemaCompatible: Boolean,
    val degradeMessage: String?,
) {
    fun lookupByPair(from: String, to: String): List<AssetTrain> =
        trains.filter { it.fromStation == from && it.toStation == to }
}

object OfflineAssetCodec {
    const val SUPPORTED_SCHEMA = 2

    fun parse(raw: String): OfflineAsset {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) { // named tolerance: corrupt asset -> honest empty
            return OfflineAsset(0, emptyList(), false, "the offline data on this phone is unreadable")
        }
        val schema = json.optInt("schema", -1)
        if (schema != SUPPORTED_SCHEMA) {
            return OfflineAsset(schema, emptyList(), false,
                "this phone's offline data is from an older format; showing unverified data only")
        }
        val trainsJson = json.optJSONArray("trains")
            ?: return OfflineAsset(schema, emptyList(), true, null)
        val trains = buildList {
            for (i in 0 until trainsJson.length()) {
                val t = trainsJson.optJSONObject(i) ?: continue
                val number = t.optString("number", "").trim()
                val name = t.optString("name", "").trim()
                val from = t.optString("from", "").trim().uppercase()
                val to = t.optString("to", "").trim().uppercase()
                val departure = t.optString("departure", "").trim()
                if (number.isBlank() || name.isBlank() || from.isBlank() || to.isBlank()) continue
                add(
                    AssetTrain(
                        number = number, name = name, fromStation = from, toStation = to,
                        departure = departure, arrival = t.optString("arrival", "").trim(),
                        type = t.optString("type", "").trim(), zone = t.optString("zone", "").trim(),
                    )
                )
            }
        }
        return OfflineAsset(schema, trains, true, null)
    }

    fun emptyAsset() = OfflineAsset(SUPPORTED_SCHEMA, emptyList(), true, null)
}
