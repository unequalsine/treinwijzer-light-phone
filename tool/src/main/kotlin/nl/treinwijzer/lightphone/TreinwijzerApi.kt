package nl.treinwijzer.lightphone

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

class TreinwijzerApi(
    private val baseUrl: String = BuildConfig.WORKER_BASE_URL.trimEnd('/'),
    private val appToken: String = BuildConfig.WORKER_ACCESS_TOKEN,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 12_000
        }
    }

    suspend fun registerInstall(installId: String): InstallRegistrationResponse = checked(
        client.post("$baseUrl/install/register") {
            commonHeaders(installId, null)
            contentType(ContentType.Application.Json)
            setBody(InstallRegistrationRequest(installId))
        },
    ).body()

    suspend fun stations(identity: InstallIdentity): List<Station> = checked(
        client.get("$baseUrl/stations") { commonHeaders(identity) },
    ).body()

    suspend fun searchStations(
        identity: InstallIdentity,
        query: String,
        language: Language,
    ): List<Station> = checked(
        client.get("$baseUrl/stations/search") {
            commonHeaders(identity)
            parameter("q", query)
            parameter("locale", language.nsCode)
        },
    ).body()

    suspend fun departures(identity: InstallIdentity, station: Station, language: Language): List<Departure> = checked(
        client.get("$baseUrl/stations/${station.code}/departures") {
            commonHeaders(identity)
            parameter("durationMinutes", 90)
            parameter("lang", language.nsCode)
        },
    ).body()

    suspend fun departureDetail(
        identity: InstallIdentity,
        station: Station,
        departure: Departure,
        language: Language,
    ): Departure = checked(
        client.get("$baseUrl/stations/${station.code}/departures/detail") {
            commonHeaders(identity)
            parameter("id", departure.id)
            departure.trainNumber?.let { parameter("trainNumber", it) }
            parameter("direction", departure.direction)
            parameter("plannedDateTime", departure.plannedDateTime)
            parameter("lang", language.nsCode)
        },
    ).body()

    suspend fun disruptions(identity: InstallIdentity, station: Station, language: Language): List<Disruption> = checked(
        client.get("$baseUrl/stations/${station.code}/disruptions") {
            commonHeaders(identity)
            parameter("lang", language.nsCode)
        },
    ).body()

    suspend fun trips(identity: InstallIdentity, request: PlannerRequest, language: Language): List<TripOption> = checked(
        client.get("$baseUrl/trips") {
            commonHeaders(identity)
            parameter("from", request.origin.code)
            parameter("to", request.destination.code)
            request.via?.takeUnless { it.code == request.origin.code || it.code == request.destination.code }
                ?.let { parameter("viaStation", it.code) }
            request.dateTime?.let { parameter("dateTime", it) }
            parameter("searchForArrival", request.timeMode == PlannerTimeMode.ARRIVAL)
            parameter("lookBackMinutes", if (request.timeMode == PlannerTimeMode.ARRIVAL) 60 else 1)
            parameter("lookAheadMinutes", if (request.timeMode == PlannerTimeMode.ARRIVAL) 5 else 60)
            parameter("includePrices", true)
            parameter("lang", language.nsCode)
        },
    ).body()

    suspend fun recoveryTrips(
        identity: InstallIdentity,
        from: StationStop,
        to: StationStop,
        dateTime: String,
        language: Language,
    ): List<TripOption> = checked(
        client.get("$baseUrl/journeys/recovery") {
            commonHeaders(identity)
            parameter("from", from.code)
            parameter("to", to.code)
            parameter("dateTime", dateTime)
            parameter("lang", language.nsCode)
        },
    ).body()

    suspend fun registerPush(identity: InstallIdentity, endpoint: String): OkResponse = checked(
        client.post("$baseUrl/push/register/light") {
            commonHeaders(identity)
            contentType(ContentType.Application.Json)
            setBody(LightPushRegistration(identity.installId, endpoint))
        },
    ).body()

    suspend fun registerActiveJourney(
        identity: InstallIdentity,
        journey: TripOption,
        language: Language,
        preferences: JourneyNotificationPreferences,
    ): OkResponse = checked(
        client.post("$baseUrl/journeys/active") {
            commonHeaders(identity)
            contentType(ContentType.Application.Json)
            setBody(ActiveJourneyRegistration(identity.installId, journey, language.localeCode, preferences))
        },
    ).body()

    suspend fun activeJourney(identity: InstallIdentity): TripOption? = checked(
        client.get("$baseUrl/journeys/active") { commonHeaders(identity) },
    ).body()

    suspend fun clearActiveJourney(identity: InstallIdentity): OkResponse = checked(
        client.delete("$baseUrl/journeys/active") {
            commonHeaders(identity)
            contentType(ContentType.Application.Json)
            setBody(InstallRegistrationRequest(identity.installId))
        },
    ).body()

    suspend fun events(identity: InstallIdentity): List<LightAlert> = checked(
        client.get("$baseUrl/journeys/active/events") { commonHeaders(identity) },
    ).body()

    suspend fun acknowledgeEvents(identity: InstallIdentity, ids: List<String>): OkResponse = checked(
        client.post("$baseUrl/journeys/active/events/ack") {
            commonHeaders(identity)
            contentType(ContentType.Application.Json)
            setBody(EventAcknowledgement(ids))
        },
    ).body()

    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders(identity: InstallIdentity) =
        commonHeaders(identity.installId, identity.installSecret)

    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders(installId: String, installSecret: String?) {
        authenticationHeaders(appToken, installId, installSecret).forEach { (name, value) -> header(name, value) }
    }

    private suspend fun checked(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        val body = response.bodyAsText().take(500)
        throw ApiException(body.ifBlank { "Worker request failed" }, response.status.value)
    }

    companion object {
        internal fun authenticationHeaders(appToken: String, installId: String, installSecret: String?): Map<String, String> =
            buildMap {
                put(HttpHeaders.Accept, ContentType.Application.Json.toString())
                put("X-Treinwijzer-Install-ID", installId)
                if (appToken.isNotBlank()) put("X-Treinwijzer-App-Token", appToken)
                if (!installSecret.isNullOrBlank()) put("X-Treinwijzer-Install-Secret", installSecret)
            }
    }
}

val Language.nsCode: String get() = if (this == Language.ENGLISH) "en" else "nl"
val Language.localeCode: String get() = if (this == Language.ENGLISH) "en_GB" else "nl_NL"
