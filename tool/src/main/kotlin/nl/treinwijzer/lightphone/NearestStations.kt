package nl.treinwijzer.lightphone

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun usableLocation(latitude: Double?, longitude: Double?, accuracyMeters: Double?, timestampMs: Long?, nowMs: Long): Boolean =
    latitude != null && latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude != null && longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters in 0.0..5_000.0 &&
        timestampMs != null && nowMs - timestampMs in 0L..120_000L

internal fun nearestStations(stations: List<Station>, latitude: Double, longitude: Double): List<Station> {
    if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return emptyList()
    val originLatitude = Math.toRadians(latitude)
    return stations.asSequence()
        .filter { it.lat?.let { value -> value.isFinite() && value in -90.0..90.0 } == true }
        .filter { it.lng?.let { value -> value.isFinite() && value in -180.0..180.0 } == true }
        .sortedBy { station ->
            val stationLatitude = Math.toRadians(station.lat!!)
            val latitudeDelta = stationLatitude - originLatitude
            val longitudeDelta = Math.toRadians(station.lng!! - longitude)
            val a = (sin(latitudeDelta / 2).let { it * it } +
                cos(originLatitude) * cos(stationLatitude) * sin(longitudeDelta / 2).let { it * it }).coerceIn(0.0, 1.0)
            atan2(sqrt(a), sqrt(1 - a))
        }
        .take(8)
        .toList()
}
