package io.opentrack.app.asset

import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity
import org.json.JSONObject

/**
 * The shipped offline asset (6.7: <= 6 MB gzipped). Schema has its own version and a mismatch
 * DEGRADES with a stated message, never a crash (11.3).
 */
data class OfflineAsset(
    val schemaVersion: Int,
    val trains: List<TrainIdentity>,
    val schemaCompatible: Boolean,
    val degradeMessage: String?,
)

object OfflineAssetCodec {
    const val SUPPORTED_SCHEMA = 1

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
        val trainsJson = json.optJSONArray("trains") ?: return emptyAsset()
        val trains = buildList {
            for (i in 0 until trainsJson.length()) {
                val t = trainsJson.optJSONObject(i) ?: continue
                add(
                    TrainIdentity(
                        number = t.optString("number", ""),
                        name = t.optString("name", ""),
                        fromStation = t.optString("from", ""),
                        toStation = t.optString("to", ""),
                        provenance = Provenance(
                            kind = ProvenanceKind.TIMETABLE,
                            source = "offline asset",
                            fetchedAtMillis = 0, // least trusted until a real stamp exists
                        ),
                    )
                )
            }
        }.filter { it.number.isNotBlank() && it.name.isNotBlank() }
        return OfflineAsset(schema, trains, true, null)
    }

    fun emptyAsset() = OfflineAsset(SUPPORTED_SCHEMA, emptyList(), true, null)
}
