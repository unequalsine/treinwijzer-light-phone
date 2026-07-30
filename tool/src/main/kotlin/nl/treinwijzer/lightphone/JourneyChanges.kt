package nl.treinwijzer.lightphone

internal enum class JourneyChangeKind {
    CANCELLED,
    DISRUPTED,
    PLATFORM_CHANGED,
}

internal fun TripOption.primaryChangeKind(): JourneyChangeKind? = when {
    status.equals("cancelled", ignoreCase = true) || legs.any(TripLeg::cancelled) ->
        JourneyChangeKind.CANCELLED
    status.equals("disrupted", ignoreCase = true) || disruptions.isNotEmpty() ->
        JourneyChangeKind.DISRUPTED
    status.equals("platformChanged", ignoreCase = true) || legs.any(TripLeg::hasPlatformChange) ->
        JourneyChangeKind.PLATFORM_CHANGED
    else -> null
}

internal fun TripLeg.hasPlatformChange(): Boolean =
    platformChanged(plannedDeparturePlatform, actualDeparturePlatform) ||
        platformChanged(plannedArrivalPlatform, actualArrivalPlatform)

internal fun platformChanged(planned: String?, actual: String?): Boolean {
    val plannedValue = planned?.trim().orEmpty()
    val actualValue = actual?.trim().orEmpty()
    return plannedValue.isNotEmpty() &&
        actualValue.isNotEmpty() &&
        !plannedValue.equals(actualValue, ignoreCase = true)
}

internal fun displayedPlatform(planned: String?, actual: String?): String? =
    actual?.trim()?.takeIf(String::isNotEmpty)
        ?: planned?.trim()?.takeIf(String::isNotEmpty)

