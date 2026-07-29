package nl.treinwijzer.lightphone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val code: String,
    val name: String,
    val countryCode: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val synonyms: List<String> = emptyList(),
)

@Serializable
data class StationStop(val code: String, val name: String)

@Serializable
data class JourneyPrice(val amountEuroCents: Int, val currency: String = "EUR")

@Serializable
data class JourneySupplement(
    val type: String,
    val amountEuroCents: Int,
    val currency: String = "EUR",
)

@Serializable
data class TripLegStop(
    val id: String,
    val code: String? = null,
    val name: String,
    val cancelled: Boolean? = null,
    val plannedDeparture: String? = null,
    val actualDeparture: String? = null,
    val plannedArrival: String? = null,
    val actualArrival: String? = null,
    val plannedPlatform: String? = null,
    val actualPlatform: String? = null,
)

@Serializable
data class Disruption(
    val id: String,
    val title: String,
    val type: String,
    val severity: String,
    val trajectories: List<String> = emptyList(),
    val cause: String? = null,
    val situation: String? = null,
    val expectedDuration: String? = null,
    val description: String? = null,
    val advice: String? = null,
    val advices: List<String> = emptyList(),
    val consequences: List<String> = emptyList(),
    val alternativeTransport: String? = null,
    val period: String? = null,
    val additionalTravelTime: String? = null,
    val start: String? = null,
    val end: String? = null,
    val stationCodes: List<String> = emptyList(),
)

@Serializable
data class TripLeg(
    val id: String,
    val origin: StationStop,
    val destination: StationStop,
    val routeStations: List<String> = emptyList(),
    val stops: List<TripLegStop> = emptyList(),
    val trainType: String,
    val trainNumber: String? = null,
    val serviceDestinationName: String? = null,
    val crowding: String? = null,
    val trainLength: String? = null,
    val plannedDeparture: String,
    val actualDeparture: String,
    val plannedArrival: String,
    val actualArrival: String,
    val plannedDeparturePlatform: String? = null,
    val actualDeparturePlatform: String? = null,
    val plannedArrivalPlatform: String? = null,
    val actualArrivalPlatform: String? = null,
    val departureDelayMinutes: Int,
    val arrivalDelayMinutes: Int,
    val cancelled: Boolean,
    val messages: List<String> = emptyList(),
    val supplements: List<JourneySupplement> = emptyList(),
    val transferMinutesAfterLeg: Int? = null,
    val arrivalExitSide: String? = null,
)

@Serializable
data class TripOption(
    val id: String,
    val routeId: String? = null,
    val departure: String,
    val plannedDeparture: String,
    val arrival: String,
    val plannedArrival: String,
    val durationMinutes: Int,
    val transfers: Int,
    val status: String,
    val delayMinutes: Int,
    val legs: List<TripLeg>,
    val disruptions: List<Disruption> = emptyList(),
    val price: JourneyPrice? = null,
    val firstClassPrice: JourneyPrice? = null,
    val supplementPrice: JourneyPrice? = null,
)

@Serializable
data class DepartureStop(
    val id: String,
    val code: String? = null,
    val name: String,
    val plannedDeparture: String? = null,
    val actualDeparture: String? = null,
    val plannedArrival: String? = null,
    val actualArrival: String? = null,
    val plannedPlatform: String? = null,
    val actualPlatform: String? = null,
)

@Serializable
data class Departure(
    val id: String,
    val direction: String,
    val trainType: String,
    val trainNumber: String? = null,
    val plannedDateTime: String,
    val actualDateTime: String,
    val plannedTrack: String? = null,
    val actualTrack: String? = null,
    val delayMinutes: Int,
    val cancelled: Boolean,
    val routeStations: List<String> = emptyList(),
    val stops: List<DepartureStop> = emptyList(),
    val crowding: String? = null,
    val trainLength: String? = null,
    val messages: List<String> = emptyList(),
)

@Serializable
data class FavouriteRoute(
    val id: String,
    val name: String,
    val origin: Station,
    val destination: Station,
    val via: Station? = null,
)

@Serializable
data class JourneyNotificationPreferences(
    val guidanceEnabled: Boolean = false,
    val alertsEnabled: Boolean = true,
)

@Serializable
data class ActiveJourneyRegistration(
    val installId: String,
    val journey: TripOption,
    val locale: String,
    val notificationPreferences: JourneyNotificationPreferences,
)

@Serializable
data class InstallRegistrationRequest(val installId: String)

@Serializable
data class InstallRegistrationResponse(val installId: String, val installSecret: String)

@Serializable
data class LightPushRegistration(val installId: String, val endpoint: String)

@Serializable
data class LightAlert(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class LightPushEnvelope(
    val version: Int,
    val kind: String,
    val reason: String,
    val journeyId: String,
    val updateId: String,
    val workerPolledAt: String,
    val alert: LightAlert? = null,
)

@Serializable
data class EventAcknowledgement(val ids: List<String>)

@Serializable
data class OkResponse(val ok: Boolean)

@Serializable
enum class PlannerTimeMode {
    @SerialName("departure") DEPARTURE,
    @SerialName("arrival") ARRIVAL,
}

@Serializable
data class PlannerPreferences(
    val timeMode: PlannerTimeMode = PlannerTimeMode.DEPARTURE,
    val dateTime: String? = null,
)

@Serializable
enum class Language { ENGLISH, DUTCH }

data class PlannerRequest(
    val origin: Station,
    val destination: Station,
    val via: Station? = null,
    val timeMode: PlannerTimeMode = PlannerTimeMode.DEPARTURE,
    val dateTime: String? = null,
)

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}
