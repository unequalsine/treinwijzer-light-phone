package nl.treinwijzer.lightphone

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContractAndLogicTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesWorkerTripFixture() {
        val trip = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
        assertEquals("journey-contract-1", trip.id)
        assertEquals("7", trip.legs.single().actualDeparturePlatform)
        assertEquals(920, trip.price?.amountEuroCents)
    }

    @Test
    fun decodesVersionedLightPushFixture() {
        val push = json.decodeFromString<LightPushEnvelope>(fixture("light-push-envelope.json"))
        assertEquals(1, push.version)
        assertEquals("journey-alert", push.kind)
        assertEquals("Platform changed", push.alert?.title)
    }

    @Test
    fun stationSearchMatchesCodesSynonymsAndDiacritics() {
        val stations = listOf(
            Station("HGL", "Hengelo", "NL", synonyms = listOf("Hengelo Overijssel")),
            Station("SHL", "Schiphol Airport", "NL", synonyms = listOf("Luchthaven Schiphol")),
            Station("MTR", "Maastricht Randwyck", "NL"),
        )
        assertEquals("SHL", TreinwijzerViewModel.filterStations(stations, "luchthaven").single().code)
        assertEquals("MTR", TreinwijzerViewModel.filterStations(stations, "maastricht").single().code)
        assertTrue(TreinwijzerViewModel.filterStations(stations, "hgl hengelo").isNotEmpty())
    }

    @Test
    fun stationIndexMatchesDutchPrefixesAndSynonyms() {
        val stations = listOf(
            Station("GVC", "Den Haag Centraal", "NL"),
            Station("HT", "'s-Hertogenbosch", "NL", synonyms = listOf("Den Bosch")),
            Station("ASD", "Amsterdam Centraal", "NL"),
        )

        assertEquals(listOf("GVC", "HT"), TreinwijzerViewModel.filterStationsByLetter(stations, "H").map(Station::code))
        assertEquals(listOf("HT"), TreinwijzerViewModel.filterStationsByLetter(stations, "S").map(Station::code))
        assertEquals(listOf("ASD"), TreinwijzerViewModel.filterStationsByLetter(stations, "a").map(Station::code))
        assertEquals(
            listOf("A", "B", "D", "H", "S"),
            TreinwijzerViewModel.availableStationIndexLetters(stations),
        )
    }

    @Test
    fun activeJourneyMatchingUsesRouteFallback() {
        val original = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
        val changed = original.copy(id = "new-id", delayMinutes = 9)
        val match = TreinwijzerViewModel.matchJourney(original, listOf(changed))
        assertNotNull(match)
        assertEquals(9, match.delayMinutes)
    }

    @Test
    fun apiAuthenticationIncludesSecretOnlyAfterRegistration() {
        val registration = TreinwijzerApi.authenticationHeaders("app-token", "install-1", null)
        assertEquals("app-token", registration["X-Treinwijzer-App-Token"])
        assertEquals("install-1", registration["X-Treinwijzer-Install-ID"])
        assertTrue("X-Treinwijzer-Install-Secret" !in registration)

        val authenticated = TreinwijzerApi.authenticationHeaders("app-token", "install-1", "install-secret")
        assertEquals("install-secret", authenticated["X-Treinwijzer-Install-Secret"])
    }

    @Test
    fun persistsFavouritesPlannerPreferencesAndActiveJourney() = runBlocking {
        val directory = Files.createTempDirectory("treinwijzer-preferences-test").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) {
                directory.resolve("treinwijzer.preferences_pb")
            }
            val preferences = TreinwijzerPreferences(store)
            val trip = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
            val station = Station("UT", "Utrecht Centraal", "NL")
            val expected = PersistedState(
                installId = "install-1",
                installSecret = "install-secret",
                favouriteStations = listOf(station),
                favouriteRoutes = listOf(FavouriteRoute("route-1", "Naar Amsterdam", station, Station("ASD", "Amsterdam Centraal", "NL"))),
                recentStations = listOf(station),
                plannerPreferences = PlannerPreferences(PlannerTimeMode.ARRIVAL, "2026-08-01T12:30:00Z"),
                notificationPreferences = JourneyNotificationPreferences(guidanceEnabled = true, alertsEnabled = false),
                activeJourney = trip,
                language = Language.ENGLISH,
                registeredPushEndpoint = "https://push.example.test/push/token",
            )

            preferences.save(expected)
            assertEquals(expected, preferences.load())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun plannerRequestRetainsViaArrivalModeAndFutureDate() {
        val request = PlannerRequest(
            origin = Station("UT", "Utrecht Centraal", "NL"),
            destination = Station("RTD", "Rotterdam Centraal", "NL"),
            via = Station("GVC", "Den Haag Centraal", "NL"),
            timeMode = PlannerTimeMode.ARRIVAL,
            dateTime = "2026-08-14T16:45:00Z",
        )

        assertEquals("GVC", request.via?.code)
        assertEquals(PlannerTimeMode.ARRIVAL, request.timeMode)
        assertEquals("2026-08-14T16:45:00Z", request.dateTime)
    }

    @Test
    fun formatsNsCompactOffsetTimestampsInAmsterdamTime() {
        assertEquals("18:50", time("2026-07-29T18:50:00+0200"))
        assertEquals("18:50", time("2026-07-29T16:50:00Z"))
    }

    @Test
    fun distinguishesPlannedMaintenanceFromUnexpectedDisruptions() {
        val maintenance = Disruption(
            id = "disruption-1",
            title = "Engineering works",
            type = "MAINTENANCE",
            severity = "2",
        )
        val disruption = maintenance.copy(type = "DISRUPTION", severity = "1")

        assertTrue(maintenance.isMaintenance)
        assertTrue(!disruption.isMaintenance)
        assertEquals("Planned maintenance", disruptionCategory(maintenance, Copy(Language.ENGLISH)))
        assertEquals("Geplande werkzaamheden", disruptionCategory(maintenance, Copy(Language.DUTCH)))
        assertEquals("Disruption", disruptionCategory(disruption, Copy(Language.ENGLISH)))
        assertEquals("Storing", disruptionCategory(disruption, Copy(Language.DUTCH)))
        assertEquals("Disruption", disruptionCategory(disruption.copy(type = "unknown"), Copy(Language.ENGLISH)))
    }

    @Test
    fun screenHistoryReturnsThroughNestedScreens() {
        val history = ScreenHistory()
        val picker = ScreenMode.StationPicker(StationPurpose.INFO)
        val index = ScreenMode.StationIndex(StationPurpose.INFO)

        history.record(ScreenMode.Home, picker)
        history.record(picker, index)
        history.record(index, ScreenMode.StationResults(StationPurpose.INFO, "A", emptyList()))

        assertEquals(index, history.previous())
        assertEquals(picker, history.previous())
        assertEquals(ScreenMode.Home, history.previous())
    }

    @Test
    fun screenHistoryCanDiscardPickerLayersWhenReturningToPlanner() {
        val history = ScreenHistory()
        val picker = ScreenMode.StationPicker(StationPurpose.ORIGIN)
        val index = ScreenMode.StationIndex(StationPurpose.ORIGIN)

        history.record(ScreenMode.Home, ScreenMode.Planner)
        history.record(ScreenMode.Planner, picker)
        history.record(picker, index)

        assertEquals(ScreenMode.Planner, history.previousMatching { it == ScreenMode.Planner })
        assertEquals(ScreenMode.Home, history.previous())
    }

    @Test
    fun dateTimePickerDefaultsToNextFiveMinuteSlot() {
        val now = ZonedDateTime.parse("2026-07-29T14:02:30+02:00[Europe/Amsterdam]")

        assertEquals(
            PlannerDateTimeSelection(dayOffset = 0, hour = 14, minute = 10),
            initialPlannerDateTimeSelection(persistedValue = null, now = now),
        )
    }

    @Test
    fun dateTimePickerRestoresFutureSelectionForTomorrow() {
        val now = ZonedDateTime.parse("2026-07-29T14:02:30+02:00[Europe/Amsterdam]")

        assertEquals(
            PlannerDateTimeSelection(dayOffset = 1, hour = 10, minute = 47),
            initialPlannerDateTimeSelection(persistedValue = "2026-07-30T08:47:00Z", now = now),
        )
    }

    @Test
    fun dateTimePickerRejectsPersistedSelectionBeyondTomorrow() {
        val now = ZonedDateTime.parse("2026-07-29T14:02:30+02:00[Europe/Amsterdam]")

        assertEquals(
            PlannerDateTimeSelection(dayOffset = 0, hour = 14, minute = 10),
            initialPlannerDateTimeSelection(persistedValue = "2026-07-31T08:47:00Z", now = now),
        )
    }

    @Test
    fun dateTimePickerCarriesTimeAcrossMidnightWithinRange() {
        assertEquals(
            PlannerDateTimeSelection(dayOffset = 1, hour = 0, minute = 0),
            shiftPlannerDateTimeSelection(
                PlannerDateTimeSelection(dayOffset = 0, hour = 23, minute = 55),
                minutes = 5,
            ),
        )
        assertEquals(
            PlannerDateTimeSelection(dayOffset = 1, hour = 23, minute = 59),
            shiftPlannerDateTimeSelection(
                PlannerDateTimeSelection(dayOffset = 1, hour = 23, minute = 55),
                minutes = 5,
            ),
        )
    }

    @Test
    fun dateTimePickerProducesAmsterdamInstant() {
        assertEquals(
            "2026-07-30T06:30:00Z",
            plannerDateTimeInstant(
                PlannerDateTimeSelection(dayOffset = 1, hour = 8, minute = 30),
                today = LocalDate.of(2026, 7, 29),
            ),
        )
    }

    @Test
    fun bothLocalisationsCoverSdkLimitations() {
        assertTrue(Copy(Language.ENGLISH).nearestUnavailable.contains("not supported"))
        assertTrue(Copy(Language.DUTCH).nearestUnavailable.contains("niet ondersteund"))
        assertTrue(Copy(Language.ENGLISH).notificationsLimited.contains("closed"))
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("fixtures/$name"),
    ).readText()
}
