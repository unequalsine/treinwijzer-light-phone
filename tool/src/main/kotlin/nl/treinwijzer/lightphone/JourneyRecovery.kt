package nl.treinwijzer.lightphone

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal data class JourneyRecoveryRoute(
    val from: StationStop,
    val to: StationStop,
    val dateTime: Instant,
)

private val recoveryCompactOffsetFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")

private fun recoveryInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, recoveryCompactOffsetFormatter).toInstant() }.getOrNull()

private fun recoveryRoute(
    from: StationStop,
    to: StationStop,
    dateTime: Instant,
): JourneyRecoveryRoute? =
    if (from.code.equals(to.code, ignoreCase = true)) null else JourneyRecoveryRoute(from, to, dateTime)

internal fun TripOption.activeRecoveryRoute(now: Instant): JourneyRecoveryRoute? {
    val destination = legs.lastOrNull()?.destination ?: return null

    legs.zipWithNext().firstNotNullOfOrNull { (previousLeg, nextLeg) ->
        val previousArrival = recoveryInstant(previousLeg.actualArrival) ?: return@firstNotNullOfOrNull null
        val nextDeparture = recoveryInstant(nextLeg.actualDeparture) ?: return@firstNotNullOfOrNull null
        val connectionIsMissed = previousLeg.destination.code.equals(nextLeg.origin.code, ignoreCase = true) &&
            previousArrival > nextDeparture
        if (nextLeg.cancelled || connectionIsMissed) {
            recoveryRoute(previousLeg.destination, destination, previousArrival)
        } else {
            null
        }
    }?.let { return it }

    val firstLeg = legs.firstOrNull() ?: return null
    val firstDeparture = recoveryInstant(firstLeg.actualDeparture) ?: return null
    val cancelledLeg = legs.firstOrNull { it.cancelled }
    if (cancelledLeg != null || status.equals("CANCELLED", ignoreCase = true)) {
        val affectedLeg = cancelledLeg ?: firstLeg
        val affectedDeparture = recoveryInstant(affectedLeg.actualDeparture) ?: return null
        return recoveryRoute(affectedLeg.origin, destination, maxOf(now, affectedDeparture))
    }

    if (now <= firstDeparture.plusSeconds(5 * 60L)) {
        return recoveryRoute(firstLeg.origin, destination, now)
    }

    for (index in 0 until legs.lastIndex) {
        val leg = legs[index]
        val nextLeg = legs[index + 1]
        val arrival = recoveryInstant(leg.actualArrival) ?: continue
        val nextDeparture = recoveryInstant(nextLeg.actualDeparture) ?: continue

        if (now < arrival) {
            return recoveryRoute(leg.destination, destination, arrival)
        }

        if (now < nextDeparture.plusSeconds(60)) {
            return recoveryRoute(leg.destination, destination, maxOf(now, arrival))
        }
    }

    return null
}
