package nl.treinwijzer.lightphone

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun recoveryJourneyUsesCurrentTransferInMultiTransferJourney() {
        val trip = multiTransferTrip()

        val route = trip.activeRecoveryRoute(Instant.parse("2026-07-29T19:01:30Z"))

        assertNotNull(route)
        assertEquals("GVC", route.from.code)
        assertEquals("ASD", route.to.code)
        assertEquals(Instant.parse("2026-07-29T19:01:30Z"), route.dateTime)
    }

    @Test
    fun recoveryJourneyUsesNextStationWhileTravellingAfterFirstTransfer() {
        val trip = multiTransferTrip()

        val route = trip.activeRecoveryRoute(Instant.parse("2026-07-29T18:55:00Z"))

        assertNotNull(route)
        assertEquals("GVC", route.from.code)
        assertEquals(Instant.parse("2026-07-29T19:00:00Z"), route.dateTime)
    }

    @Test
    fun recoveryJourneyIsUnavailableOnFinalLeg() {
        val route = multiTransferTrip().activeRecoveryRoute(Instant.parse("2026-07-29T19:10:00Z"))

        assertEquals(null, route)
    }

    @Test
    fun recoveryJourneyKeepsMissedConnectionStation() {
        val trip = multiTransferTrip().let { journey ->
            journey.copy(
                legs = journey.legs.mapIndexed { index, leg ->
                    if (index == 1) leg.copy(actualDeparture = "2026-07-29T18:49:00Z") else leg
                },
            )
        }

        val route = trip.activeRecoveryRoute(Instant.parse("2026-07-29T19:01:30Z"))

        assertNotNull(route)
        assertEquals("UT", route.from.code)
        assertEquals(Instant.parse("2026-07-29T18:50:00Z"), route.dateTime)
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
    fun departureStopTimelineUsesPlannedTimeWithCalculatedDelay() {
        val departureStop = DepartureStop(
            id = "stop-1",
            name = "Zoetermeer",
            plannedDeparture = "2026-07-30T09:55:00+0200",
            actualDeparture = "2026-07-30T09:58:00+0200",
            plannedArrival = "2026-07-30T09:54:00+0200",
            actualArrival = "2026-07-30T09:57:00+0200",
        )
        val arrivalOnlyStop = departureStop.copy(
            id = "stop-2",
            plannedDeparture = null,
            actualDeparture = null,
        )

        assertEquals("2026-07-30T09:55:00+0200", departureStopPlannedTime(departureStop))
        assertEquals(3, departureStopDelayMinutes(departureStop))
        assertEquals("2026-07-30T09:54:00+0200", departureStopPlannedTime(arrivalOnlyStop))
        assertEquals(3, departureStopDelayMinutes(arrivalOnlyStop))
    }

    @Test
    fun liveTimelineOnlyMarksCompletedMomentsAsPast() {
        val now = Instant.parse("2026-07-29T19:00:00Z")

        assertTrue(timelineMomentHasPassed("2026-07-29T18:59:59Z", now))
        assertTrue(timelineMomentHasPassed("2026-07-29T21:00:00+0200", now))
        assertTrue(!timelineMomentHasPassed("2026-07-29T19:00:01Z", now))
        assertTrue(!timelineMomentHasPassed("not-a-timestamp", now))
        assertTrue(!timelineMomentHasPassed("2026-07-29T18:59:59Z", null))
    }

    @Test
    fun journeyServiceSequenceKeepsLegOrderWithoutTrainNumbers() {
        val trip = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
        val leg = trip.legs.single()
        val services = trip.copy(
            legs = listOf(
                leg.copy(trainType = "IC", trainNumber = "1234"),
                leg.copy(trainType = " SPR ", trainNumber = "5678"),
                leg.copy(trainType = "IC", trainNumber = "9012"),
                leg.copy(trainType = " ", trainNumber = "3456"),
            ),
        )

        assertEquals(listOf("IC", "SPR", "IC"), journeyServiceLabels(services))
    }

    @Test
    fun journeyChangesPrioritiseCancellationAndExposeOtherModifications() {
        val trip = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
        val unchangedLeg = trip.legs.single().copy(
            actualDeparturePlatform = trip.legs.single().plannedDeparturePlatform,
        )

        assertEquals(JourneyChangeKind.PLATFORM_CHANGED, trip.primaryChangeKind())
        assertEquals(
            JourneyChangeKind.DISRUPTED,
            trip.copy(status = "disrupted", legs = listOf(unchangedLeg)).primaryChangeKind(),
        )
        assertEquals(
            JourneyChangeKind.CANCELLED,
            trip.copy(
                status = "disrupted",
                legs = listOf(unchangedLeg.copy(cancelled = true)),
            ).primaryChangeKind(),
        )
        assertEquals(
            JourneyChangeKind.CANCELLED,
            trip.copy(status = "CANCELLED", legs = listOf(unchangedLeg)).primaryChangeKind(),
        )
        assertEquals(
            null,
            trip.copy(status = "normal", disruptions = emptyList(), legs = listOf(unchangedLeg)).primaryChangeKind(),
        )
        assertEquals(
            null,
            trip.copy(status = "delayed", disruptions = emptyList(), legs = listOf(unchangedLeg)).primaryChangeKind(),
        )
    }

    @Test
    fun platformChangesRequireTwoDifferentUsableValues() {
        assertTrue(platformChanged("5", "7"))
        assertTrue(platformChanged(" 5 ", "7"))
        assertTrue(!platformChanged("5", "5"))
        assertTrue(!platformChanged("5", " 5 "))
        assertTrue(!platformChanged("5", null))
        assertTrue(!platformChanged(null, "7"))
        assertEquals("7", displayedPlatform("5", " 7 "))
        assertEquals("5", displayedPlatform(" 5 ", null))
    }

    @Test
    fun departuresDisplayPlannedTimeWithDelayUnlessCancelled() {
        val departure = Departure(
            id = "departure-1",
            direction = "Rotterdam Centraal",
            trainType = "IC",
            plannedDateTime = "2026-07-30T09:55:00+0200",
            actualDateTime = "2026-07-30T10:07:00+0200",
            delayMinutes = 12,
            cancelled = false,
        )

        assertEquals("09:55+12", departureDisplayTime(departure))
        assertEquals("09:55", departureDisplayTime(departure.copy(cancelled = true)))
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
    fun screenHistoryReturnsViaSelectionToPlannerOptions() {
        val history = ScreenHistory()
        val options = ScreenMode.PlannerOptions(
            selection = PlannerDateTimeSelection(dayOffset = 1, hour = 14, minute = 25),
            timeMode = PlannerTimeMode.ARRIVAL,
        )
        val picker = ScreenMode.StationPicker(StationPurpose.VIA)
        val index = ScreenMode.StationIndex(StationPurpose.VIA)

        history.record(ScreenMode.Planner, options)
        history.record(options, picker)
        history.record(picker, index)
        history.record(index, ScreenMode.StationResults(StationPurpose.VIA, "U", emptyList()))

        assertEquals(options, history.previousMatching { it is ScreenMode.PlannerOptions })
        assertEquals(ScreenMode.Planner, history.previous())
    }

    @Test
    fun screenHistoryCanResetWhenTrackingStarts() {
        val history = ScreenHistory()
        val request = PlannerRequest(
            origin = Station("A", "Station A", "NL"),
            destination = Station("B", "Station B", "NL"),
        )
        val trips = ScreenMode.Trips(request, emptyList())

        history.record(ScreenMode.Home, ScreenMode.Planner)
        history.record(ScreenMode.Planner, trips)
        history.record(trips, ScreenMode.Settings)

        history.clear()

        assertNull(history.previous())
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

    private fun multiTransferTrip(): TripOption {
        val original = json.decodeFromString<TripOption>(fixture("trip-contract.json"))
        val template = original.legs.single()
        val zoetermeer = StationStop("ZTM", "Zoetermeer")
        val utrecht = StationStop("UT", "Utrecht Centraal")
        val denHaag = StationStop("GVC", "Den Haag Centraal")
        val amsterdam = StationStop("ASD", "Amsterdam Centraal")
        return original.copy(
            departure = "2026-07-29T18:35:00Z",
            plannedDeparture = "2026-07-29T18:35:00Z",
            arrival = "2026-07-29T19:30:00Z",
            plannedArrival = "2026-07-29T19:30:00Z",
            transfers = 2,
            legs = listOf(
                template.copy(
                    id = "leg-1",
                    origin = zoetermeer,
                    destination = utrecht,
                    plannedDeparture = "2026-07-29T18:35:00Z",
                    actualDeparture = "2026-07-29T18:35:00Z",
                    plannedArrival = "2026-07-29T18:50:00Z",
                    actualArrival = "2026-07-29T18:50:00Z",
                ),
                template.copy(
                    id = "leg-2",
                    origin = utrecht,
                    destination = denHaag,
                    plannedDeparture = "2026-07-29T18:52:00Z",
                    actualDeparture = "2026-07-29T18:52:00Z",
                    plannedArrival = "2026-07-29T19:00:00Z",
                    actualArrival = "2026-07-29T19:00:00Z",
                ),
                template.copy(
                    id = "leg-3",
                    origin = denHaag,
                    destination = amsterdam,
                    plannedDeparture = "2026-07-29T19:02:00Z",
                    actualDeparture = "2026-07-29T19:02:00Z",
                    plannedArrival = "2026-07-29T19:30:00Z",
                    actualArrival = "2026-07-29T19:30:00Z",
                ),
            ),
        )
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("fixtures/$name"),
    ).readText()
}
