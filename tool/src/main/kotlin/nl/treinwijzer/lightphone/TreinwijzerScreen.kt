package nl.treinwijzer.lightphone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@InitialScreen
class TreinwijzerScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, TreinwijzerViewModel>(sealedActivity) {
    override val viewModelClass: Class<TreinwijzerViewModel> = TreinwijzerViewModel::class.java
    override fun createViewModel() = TreinwijzerViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val colours by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val copy = Copy(state.persisted.language)
        val keyboardOptions = rememberKeyboardOptions()

        LightTheme(colors = colours) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    ScreenMode.Loading -> MessageScreen(copy.app, copy.loading)
                    ScreenMode.Home -> HomeContent(state, copy, viewModel)
                    is ScreenMode.StationInput -> {
                        val input = rememberTextFieldState("")
                        LightTextInputEditor(
                            title = copy.searchStation,
                            state = input,
                            editorKey = mode.session,
                            keyboardOptionsFlow = keyboardOptions,
                            onSubmit = { viewModel.submitStationSearch(it.toString(), mode.purpose) },
                            onBack = viewModel::home,
                            submitIcon = LightIcons.SEARCH,
                            singleLine = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is ScreenMode.StationResults -> StationResultsContent(mode, copy, viewModel)
                    is ScreenMode.Departures -> DeparturesContent(mode, state, copy, viewModel)
                    is ScreenMode.DepartureDetails -> DepartureDetailsContent(mode, copy, viewModel)
                    is ScreenMode.Disruptions -> DisruptionsContent(mode, copy, viewModel)
                    is ScreenMode.DisruptionDetails -> DisruptionDetailsContent(mode.disruption, copy, viewModel)
                    ScreenMode.Planner -> PlannerContent(state, copy, viewModel)
                    is ScreenMode.DateTimeInput -> {
                        val input = rememberTextFieldState("")
                        LightTextInputEditor(
                            title = copy.chooseTime,
                            state = input,
                            editorKey = mode.session,
                            keyboardOptionsFlow = keyboardOptions,
                            onSubmit = { viewModel.setPlannerDateTime(it.toString()) },
                            onBack = viewModel::openPlanner,
                            submitIcon = LightIcons.ACCEPT,
                            singleLine = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is ScreenMode.Trips -> TripsContent(mode, copy, viewModel)
                    is ScreenMode.TripDetails -> TripDetailsContent(mode, state, copy, viewModel)
                    ScreenMode.Favourites -> FavouritesContent(state, copy, viewModel)
                    ScreenMode.Settings -> SettingsContent(state, copy, viewModel)
                    ScreenMode.NearestUnavailable -> MessageScreen(copy.nearest, copy.nearestUnavailable, viewModel::home)
                    is ScreenMode.Active -> ActiveJourneyContent(mode.journey, copy, viewModel)
                }

                if (state.busy && state.mode != ScreenMode.Loading) {
                    LightText(
                        text = copy.loading,
                        variant = LightTextVariant.Fine,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 5f.gridUnitsAsDp()),
                    )
                }
                state.errorModal?.let { LightFullscreenModal(it, viewModel::dismissError) }
                state.alertModal?.let { LightFullscreenModal("${it.title}\n\n${it.body}", viewModel::dismissAlert) }
            }
        }
    }
}

@Composable
private fun HomeContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.app) {
        state.persisted.activeJourney?.let { journey ->
            Section(copy.live)
            ActionRow(journeyTitle(journey), "${copy.live} · ${journey.status}", vm::openActive)
        }
        ActionRow(copy.planner, "${copy.origin} · ${copy.destination}", vm::openPlanner)
        ActionRow(copy.departures, copy.searchStation) { vm.openStationSearch(StationPurpose.INFO) }
        if (state.persisted.favouriteStations.isNotEmpty()) {
            Section(copy.favouriteStations)
            state.persisted.favouriteStations.take(4).forEach { station ->
                ActionRow(station.name, station.code) { vm.loadDepartures(station) }
            }
        }
        if (state.persisted.recentStations.isNotEmpty()) {
            Section(copy.recent)
            state.persisted.recentStations.take(4).forEach { station ->
                ActionRow(station.name, station.code) { vm.loadDepartures(station) }
            }
        }
        Section(copy.more)
        ActionRow(copy.favourites, copy.favouriteRoutes, vm::openFavourites)
        ActionRow(copy.nearest, copy.nearestUnavailable, vm::openNearest)
        ActionRow(copy.settings, "${copy.alerts} · ${state.persisted.language.name.lowercase()}", vm::openSettings)
    }
}

@Composable
private fun StationResultsContent(mode: ScreenMode.StationResults, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.searchStation, vm::home) {
        if (mode.stations.isEmpty()) Body(copy.noResults)
        mode.stations.forEach { station -> ActionRow(station.name, station.code) { vm.selectStation(station, mode.purpose) } }
    }
}

@Composable
private fun DeparturesContent(mode: ScreenMode.Departures, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(mode.station.name, vm::home) {
        val favourite = state.persisted.favouriteStations.any { it.code == mode.station.code }
        ActionRow(if (favourite) copy.removeFavourite else copy.addFavourite, mode.station.code) { vm.toggleFavouriteStation(mode.station) }
        ActionRow(copy.disruptions, mode.station.name) { vm.loadDisruptions(mode.station) }
        Section(copy.departures)
        if (mode.departures.isEmpty()) Body(copy.noDepartures)
        mode.departures.forEach { departure ->
            DepartureCard(departure, copy) { vm.loadDepartureDetails(mode.station, departure) }
        }
    }
}

@Composable
private fun DepartureDetailsContent(mode: ScreenMode.DepartureDetails, copy: Copy, vm: TreinwijzerViewModel) {
    val departure = mode.departure
    ScreenFrame(departure.direction, vm::home) {
        TrainPlatformBadges(
            trainType = departure.trainType,
            trainNumber = departure.trainNumber,
            platform = departure.actualTrack ?: departure.plannedTrack,
            copy = copy,
        )
        departureStatus(departure, copy)?.let { Body(it) }
        Detail(copy.leaving, dateTime(departure.actualDateTime))
        if (departure.routeStations.isNotEmpty()) Detail(copy.destination, departure.routeStations.joinToString(" · "))
        departure.stops.forEach { stop ->
            Detail(stop.name, listOfNotNull(stop.actualDeparture?.let(::time), stop.actualPlatform?.let { "${copy.platform} $it" }).joinToString(" · "))
        }
        departure.messages.forEach { Body(it) }
    }
}

@Composable
private fun DisruptionsContent(mode: ScreenMode.Disruptions, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.disruptions, vm::home) {
        if (mode.disruptions.isEmpty()) Body(copy.noDisruptions)
        mode.disruptions.forEach { disruption -> ActionRow(disruption.title, disruption.severity) { vm.showDisruption(disruption) } }
    }
}

@Composable
private fun DisruptionDetailsContent(disruption: Disruption, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.disruptions, vm::home) {
        Heading(disruption.title)
        Detail(disruption.type, disruption.severity)
        disruption.trajectories.forEach { Body(it) }
        listOfNotNull(disruption.cause, disruption.situation, disruption.description, disruption.advice, disruption.expectedDuration, disruption.additionalTravelTime)
            .forEach { Body(it) }
        disruption.advices.forEach { Body(it) }
        disruption.consequences.forEach { Body(it) }
    }
}

@Composable
private fun PlannerContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.planner, vm::home) {
        ActionRow(copy.origin, state.plannerOrigin?.name ?: copy.searchStation) { vm.openStationSearch(StationPurpose.ORIGIN) }
        ActionRow(copy.destination, state.plannerDestination?.name ?: copy.searchStation) { vm.openStationSearch(StationPurpose.DESTINATION) }
        ActionRow(copy.via, state.plannerVia?.name ?: "–") { vm.openStationSearch(StationPurpose.VIA) }
        if (state.plannerVia != null) ActionRow(copy.close, copy.via, vm::clearVia)
        ActionRow("⇅", "${copy.origin} / ${copy.destination}", vm::swapPlannerStations)
        val preferences = state.persisted.plannerPreferences
        ActionRow(
            if (preferences.timeMode == PlannerTimeMode.DEPARTURE) copy.leaving else copy.arriving,
            if (preferences.dateTime == null) copy.now else dateTime(preferences.dateTime),
            vm::togglePlannerTimeMode,
        )
        ActionRow(copy.chooseTime, copy.dateTimeHelp, vm::openDateTimeInput)
        if (preferences.dateTime != null) ActionRow(copy.now, copy.chooseTime, vm::useCurrentTime)
        Spacer(Modifier.height(1f.gridUnitsAsDp()))
        ActionRow(copy.plan, routeSummary(state.plannerOrigin, state.plannerDestination), vm::planJourney)
    }
}

@Composable
private fun TripsContent(mode: ScreenMode.Trips, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(if (mode.recovery) copy.recovery else copy.planner, vm::openPlanner) {
        if (mode.trips.isEmpty()) Body(copy.noJourneys)
        mode.trips.forEach { trip ->
            ActionRow(
                "${time(trip.departure)}–${time(trip.arrival)}",
                tripSummary(trip, copy),
            ) { vm.showTrip(trip, mode.request) }
        }
    }
}

@Composable
private fun TripDetailsContent(mode: ScreenMode.TripDetails, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    val trip = mode.trip
    ScreenFrame(journeyTitle(trip), vm::openPlanner) {
        Detail("${time(trip.departure)}–${time(trip.arrival)}", tripSummary(trip, copy))
        trip.price?.let { Detail(copy.price, price(it)) }
        trip.firstClassPrice?.let { Detail("${copy.price} 1", price(it)) }
        trip.supplementPrice?.let { Detail("Supplement", price(it)) }
        trip.legs.forEachIndexed { index, leg ->
            Section("${index + 1}. ${leg.origin.name} → ${leg.destination.name}")
            TrainPlatformBadges(
                trainType = leg.trainType,
                trainNumber = leg.trainNumber,
                platform = leg.actualDeparturePlatform ?: leg.plannedDeparturePlatform,
                copy = copy,
            )
            Detail("${time(leg.actualDeparture)}–${time(leg.actualArrival)}", leg.serviceDestinationName.orEmpty())
            val arrivalPlatform = leg.actualArrivalPlatform ?: leg.plannedArrivalPlatform
            if (!arrivalPlatform.isNullOrBlank()) Detail("${copy.arriving} · ${copy.platform}", arrivalPlatform)
            leg.transferMinutesAfterLeg?.let { Detail(copy.transfer, "$it ${copy.minutes}") }
            leg.messages.forEach { Body(it) }
        }
        trip.disruptions.forEach { ActionRow(it.title, it.severity) { vm.showDisruption(it) } }
        mode.request?.let { ActionRow(copy.saveRoute, routeSummary(it.origin, it.destination)) { vm.saveRoute(it) } }
        ActionRow(copy.track, copy.alerts) { vm.startTracking(trip) }
        if (trip.legs.size > 1) ActionRow(copy.recovery, trip.legs.first().destination.name) { vm.loadRecovery(trip) }
        if (state.persisted.activeJourney?.id == trip.id) Body(copy.live)
    }
}

@Composable
private fun ActiveJourneyContent(journey: TripOption, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.activeJourney, vm::home) {
        Heading(journeyTitle(journey))
        Detail("${time(journey.departure)}–${time(journey.arrival)}", tripSummary(journey, copy))
        journey.legs.forEach { leg ->
            Section("${leg.origin.name} → ${leg.destination.name}")
            TrainPlatformBadges(
                trainType = leg.trainType,
                trainNumber = leg.trainNumber,
                platform = leg.actualDeparturePlatform ?: leg.plannedDeparturePlatform,
                copy = copy,
            )
            Detail("${time(leg.actualDeparture)}–${time(leg.actualArrival)}", leg.serviceDestinationName.orEmpty())
            if (leg.departureDelayMinutes > 0) Body("+${leg.departureDelayMinutes} ${copy.minutes}")
            leg.messages.forEach { Body(it) }
        }
        journey.disruptions.forEach { Body(it.title) }
        ActionRow(copy.refresh, copy.live, vm::manualRefreshActive)
        if (journey.legs.size > 1) ActionRow(copy.recovery, journey.legs.first().destination.name) { vm.loadRecovery(journey) }
        ActionRow(copy.stopTracking, copy.activeJourney, vm::stopTracking)
    }
}

@Composable
private fun FavouritesContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.favourites, vm::home) {
        Section(copy.favouriteStations)
        state.persisted.favouriteStations.forEach { station -> ActionRow(station.name, station.code) { vm.loadDepartures(station) } }
        Section(copy.favouriteRoutes)
        state.persisted.favouriteRoutes.forEach { route ->
            ActionRow(route.name, route.via?.let { "${copy.via} ${it.name}" }.orEmpty()) { vm.useRoute(route) }
            ActionRow(copy.removeFavourite, route.name) { vm.removeRoute(route) }
        }
        if (state.persisted.favouriteStations.isEmpty() && state.persisted.favouriteRoutes.isEmpty()) Body(copy.noResults)
    }
}

@Composable
private fun SettingsContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.settings, vm::home) {
        Section("Language / Taal")
        ActionRow(copy.english, if (state.persisted.language == Language.ENGLISH) "✓" else "") { vm.setLanguage(Language.ENGLISH) }
        ActionRow(copy.dutch, if (state.persisted.language == Language.DUTCH) "✓" else "") { vm.setLanguage(Language.DUTCH) }
        Section(copy.alerts)
        ActionRow(copy.alerts, if (state.persisted.notificationPreferences.alertsEnabled) copy.enabled else copy.disabled, vm::toggleAlerts)
        ActionRow(copy.guidance, if (state.persisted.notificationPreferences.guidanceEnabled) copy.enabled else copy.disabled, vm::toggleGuidance)
        Body(copy.notificationsLimited)
        Section("Worker")
        Body(if (BuildConfig.WORKER_ACCESS_TOKEN.isBlank()) copy.notConfigured else copy.configured)
    }
}

@Composable
private fun ScreenFrame(title: String, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = onBack?.let { LightBarButton.LightIcon(LightIcons.BACK, onClick = it) },
            center = LightTopBarCenter.Text(title),
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(2f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.content),
        )
        LightScrollView(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
            ) { content() }
        }
    }
}

@Composable
private fun MessageScreen(title: String, message: String, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = onBack?.let { LightBarButton.LightIcon(LightIcons.BACK, onClick = it) },
            center = LightTopBarCenter.Text(title),
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(2f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
            LightText(message, LightTextVariant.Copy, align = TextAlign.Center)
        }
        if (onBack != null) LightBottomBar(listOf(LightBarButton.LightIcon(LightIcons.BACK, onClick = onBack)))
    }
}

@Composable
private fun ActionRow(title: String, detail: String = "", onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.65f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(title, LightTextVariant.ParagraphWide, modifier = Modifier.weight(1.15f), maxLines = 2)
        if (detail.isNotBlank()) {
            LightText(
                detail,
                LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.End,
                modifier = Modifier.weight(0.85f),
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun DepartureCard(departure: Departure, copy: Copy, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.65f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(time(departure.actualDateTime), LightTextVariant.Subheading)
            LightText(
                departure.direction,
                LightTextVariant.Copy,
                align = TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 0.6f.gridUnitsAsDp()),
                maxLines = 2,
            )
        }
        TrainPlatformBadges(
            trainType = departure.trainType,
            trainNumber = departure.trainNumber,
            platform = departure.actualTrack ?: departure.plannedTrack,
            copy = copy,
        )
        departureStatus(departure, copy)?.let { status ->
            LightText(
                status,
                LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun TrainPlatformBadges(trainType: String, trainNumber: String?, platform: String?, copy: Copy) {
    Row(
        modifier = Modifier.padding(top = 0.45f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(listOfNotNull(trainType.takeIf(String::isNotBlank), trainNumber?.takeIf(String::isNotBlank)).joinToString(" "))
        platform?.takeIf(String::isNotBlank)?.let { Badge("${copy.platform} $it", inverted = true) }
    }
}

@Composable
private fun Badge(text: String, inverted: Boolean = false) {
    val background = if (inverted) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (inverted) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Box(
        Modifier
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(horizontal = 0.45f.gridUnitsAsDp(), vertical = 0.2f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text.uppercase(Locale.ROOT),
            variant = LightTextVariant.Superfine,
            color = foreground,
            monospace = true,
            maxLines = 1,
        )
    }
}

@Composable
private fun Section(text: String) {
    if (text.isBlank()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 1.2f.gridUnitsAsDp(), bottom = 0.3f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text.uppercase(Locale.ROOT),
            variant = LightTextVariant.Superfine,
            monospace = true,
            modifier = Modifier.padding(bottom = 0.35f.gridUnitsAsDp()),
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(2f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.content),
        )
    }
}
@Composable private fun Heading(text: String) = LightText(text, LightTextVariant.Subheading, modifier = Modifier.padding(vertical = 0.8f.gridUnitsAsDp()))
@Composable private fun Body(text: String) = LightText(text, LightTextVariant.Paragraph, modifier = Modifier.padding(vertical = 0.6f.gridUnitsAsDp()))
@Composable private fun Detail(title: String, detail: String) = Column(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
) {
    LightText(title, LightTextVariant.Copy)
    if (detail.isNotBlank()) LightText(detail, LightTextVariant.Detail, lighten = true)
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(top = 0.45f.gridUnitsAsDp())
            .height(1f.designVerticalPxToDp())
            .background(LightThemeTokens.colors.contentSecondary),
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
private val compactOffsetFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")
private val amsterdam = ZoneId.of("Europe/Amsterdam")
private fun parsedInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, compactOffsetFormatter).toInstant() }.getOrNull()
internal fun time(value: String): String = parsedInstant(value)?.atZone(amsterdam)?.format(timeFormatter) ?: value
internal fun dateTime(value: String): String = parsedInstant(value)?.atZone(amsterdam)?.format(dateFormatter) ?: value
private fun price(value: JourneyPrice): String = String.format(Locale.UK, "€ %.2f", value.amountEuroCents / 100.0)
private fun journeyTitle(journey: TripOption): String = "${journey.legs.firstOrNull()?.origin?.name.orEmpty()} → ${journey.legs.lastOrNull()?.destination?.name.orEmpty()}"
private fun routeSummary(from: Station?, to: Station?): String = listOfNotNull(from?.name, to?.name).joinToString(" → ")
private fun tripSummary(trip: TripOption, copy: Copy): String {
    val transferText = "${trip.transfers} ${if (trip.transfers == 1) copy.transfer else copy.transfers}"
    val delay = if (trip.delayMinutes > 0) " · +${trip.delayMinutes}" else ""
    return "${trip.durationMinutes} ${copy.minutes} · $transferText$delay"
}
private fun departureStatus(departure: Departure, copy: Copy): String? = when {
    departure.cancelled -> copy.cancelled
    departure.delayMinutes > 0 -> "+${departure.delayMinutes} ${copy.minutes}"
    else -> null
}
