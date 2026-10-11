package io.opentrack.app.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.opentrack.core.contract.Clock
import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity

/**
 * SQLite store (6.4, ADR 0001): cached listings with TTL + per-train live OVERRIDES so an
 * opened train CORRECTS the cold-start copy instead of merely contradicting it.
 * A migration failure clears the cache and says so - it never shows stale rows as fresh.
 */
class OpenTrackStore(
    context: Context,
    private val clock: Clock,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class CacheRow(
        val fromStation: String,
        val toStation: String,
        val payload: String,
        val fetchedAtMillis: Long,
    )

    data class Override(
        val number: String,
        val name: String,
        val fromStation: String,
        val toStation: String,
        val fetchedAtMillis: Long,
        val source: String,
    )

    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CACHE (
                from_station TEXT NOT NULL,
                to_station TEXT NOT NULL,
                payload TEXT NOT NULL,
                fetched_at_millis INTEGER NOT NULL,
                PRIMARY KEY (from_station, to_station)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_OVERRIDES (
                number TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                from_station TEXT NOT NULL,
                to_station TEXT NOT NULL,
                fetched_at_millis INTEGER NOT NULL,
                source TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A migration we have not written clears the cache and says so (6.4). We never pretend.
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_OVERRIDES")
        onCreate(db)
    }

    // ---- cache with TTL -------------------------------------------------------------

    fun putCache(fromStation: String, toStation: String, payload: String, atMillis: Long) {
        writableDatabase.insertWithOnConflict(
            TABLE_CACHE, null,
            ContentValues().apply {
                put("from_station", fromStation)
                put("to_station", toStation)
                put("payload", payload)
                put("fetched_at_millis", atMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** Returns payload only when the row is still fresh; expired is a miss, not a lie. */
    fun freshCache(fromStation: String, toStation: String, ttlMillis: Long): CacheRow? {
        val now = clock.nowMillis()
        readableDatabase.rawQuery(
            "SELECT payload, fetched_at_millis FROM $TABLE_CACHE WHERE from_station=? AND to_station=?",
            arrayOf(fromStation, toStation),
        ).use { c ->
            if (!c.moveToFirst()) return null
            val row = CacheRow(fromStation, toStation, c.getString(0), c.getLong(1))
            if (now - row.fetchedAtMillis > ttlMillis || row.fetchedAtMillis <= 0) return null
            return row
        }
    }

    // ---- per-train overrides --------------------------------------------------------

    fun putOverride(override: Override) {
        writableDatabase.insertWithOnConflict(
            TABLE_OVERRIDES, null,
            ContentValues().apply {
                put("number", override.number)
                put("name", override.name)
                put("from_station", override.fromStation)
                put("to_station", override.toStation)
                put("fetched_at_millis", override.fetchedAtMillis)
                put("source", override.source)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** An override CORRECTS a row: the union shows the override as a real actual (6.4). */
    fun identityFor(number: String, fallback: TrainIdentity): TrainIdentity {
        readableDatabase.rawQuery(
            "SELECT name, from_station, to_station, fetched_at_millis, source FROM $TABLE_OVERRIDES WHERE number=?",
            arrayOf(number),
        ).use { c ->
            if (!c.moveToFirst()) return fallback
            val fetched = c.getLong(3)
            return TrainIdentity(
                number = number,
                name = c.getString(0),
                fromStation = c.getString(1),
                toStation = c.getString(2),
                provenance = Provenance(
                    kind = if (fetched > 0) ProvenanceKind.LIVE else ProvenanceKind.TIMETABLE,
                    source = c.getString(4),
                    fetchedAtMillis = fetched,
                ),
            )
        }
    }

    companion object {
        const val DATABASE_NAME = "opentrack.db"
        const val DATABASE_VERSION = 1
        private const val TABLE_CACHE = "cache"
        private const val TABLE_OVERRIDES = "overrides"
    }
}
