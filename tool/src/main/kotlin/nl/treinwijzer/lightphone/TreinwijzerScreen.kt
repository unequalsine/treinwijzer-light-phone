package nl.treinwijzer.lightphone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
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
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.Duration
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
                    is ScreenMode.StationPicker -> StationPickerContent(mode, state, copy, viewModel)
                    is ScreenMode.StationIndex -> StationIndexContent(mode, copy, viewModel)
                    is ScreenMode.StationResults -> StationResultsContent(mode, copy, viewModel)
                    is ScreenMode.StationRecents -> StationRecentsContent(mode, copy, viewModel)
                    is ScreenMode.StationNearestUnavailable -> MessageScreen(
                        copy.nearest,
                        copy.nearestUnavailable,
                    ) { viewModel.openStationSearch(mode.purpose) }
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
            LiveJourneyCard(journey, copy, vm::openActive)
        }
        Section(copy.travel)
        MenuAction("01", copy.planner, "${copy.origin} → ${copy.destination}", prominent = true, onClick = vm::openPlanner)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            HomeShortcut("02", copy.departures, Modifier.weight(1f)) { vm.openStationSearch(StationPurpose.INFO) }
            HomeShortcut("03", copy.disruptions, Modifier.weight(1f)) { vm.openStationSearch(StationPurpose.DISRUPTIONS) }
        }
        if (state.persisted.favouriteStations.isNotEmpty()) {
            Section(copy.favouriteStations)
            state.persisted.favouriteStations.take(4).forEach { station ->
                ListAction(station.name, copy.departures) { vm.loadDepartures(station) }
            }
        }
        Section(copy.more)
        ListAction(copy.favourites, copy.favouriteRoutes, vm::openFavourites)
        ListAction(copy.nearest, copy.unavailable, vm::openNearest)
        ListAction(copy.settings, if (state.persisted.language == Language.ENGLISH) copy.english else copy.dutch, vm::openSettings)
    }
}

@Composable
private fun StationPickerContent(
    mode: ScreenMode.StationPicker,
    state: TreinwijzerUiState,
    copy: Copy,
    vm: TreinwijzerViewModel,
) {
    ScreenFrame(copy.chooseStation, { vm.backFromStationPicker(mode.purpose) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.25f.gridUnitsAsDp()),
        ) {
            PickerShortcut("A–Z", Modifier.weight(1f)) {
                vm.openStationIndex(mode.purpose)
            }
            PickerShortcut(copy.recents, Modifier.weight(1f)) {
                vm.openStationRecents(mode.purpose)
            }
            PickerShortcut(copy.nearestShort, Modifier.weight(1f)) {
                vm.openStationNearest(mode.purpose)
            }
        }
        Section(copy.favouriteStations)
        if (state.persisted.favouriteStations.isEmpty()) Body(copy.noFavouriteStations)
        state.persisted.favouriteStations.forEach { station ->
            ListAction(station.name) { vm.selectStation(station, mode.purpose) }
        }
    }
}

@Composable
private fun StationIndexContent(mode: ScreenMode.StationIndex, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.stationIndex, { vm.openStationSearch(mode.purpose) }) {
        Section(copy.chooseLetter)
        stationLetters.chunked(3).forEach { rowLetters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
            ) {
                rowLetters.forEach { letter ->
                    LetterButton(letter, Modifier.weight(1f)) { vm.selectStationLetter(letter, mode.purpose) }
                }
                repeat(3 - rowLetters.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StationResultsContent(mode: ScreenMode.StationResults, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(mode.query, { vm.openStationIndex(mode.purpose) }) {
        if (mode.stations.isEmpty()) Body(copy.noResults)
        mode.stations.forEach { station -> ListAction(station.name) { vm.selectStation(station, mode.purpose) } }
    }
}

@Composable
private fun StationRecentsContent(mode: ScreenMode.StationRecents, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.recents, { vm.openStationSearch(mode.purpose) }) {
        if (mode.stations.isEmpty()) Body(copy.noResults)
        mode.stations.forEach { station -> ListAction(station.name) { vm.selectStation(station, mode.purpose) } }
    }
}

@Composable
private fun DeparturesContent(mode: ScreenMode.Departures, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(mode.station.name, vm::home) {
        val favourite = state.persisted.favouriteStations.any { it.code == mode.station.code }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            UtilityAction(if (favourite) "−" else "+", if (favourite) copy.removeFavourite else copy.addFavourite, Modifier.weight(1f)) {
                vm.toggleFavouriteStation(mode.station)
            }
            UtilityAction("!", copy.disruptions, Modifier.weight(1f)) { vm.loadDisruptions(mode.station) }
        }
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
    ScreenFrame(copy.departureDetails, vm::home) {
        DepartureHero(departure, copy)
        if (departure.stops.isNotEmpty()) {
            Section(copy.routeStops)
            departure.stops.forEachIndexed { index, stop ->
                DepartureStopRow(stop, first = index == 0, last = index == departure.stops.lastIndex, copy = copy)
            }
        } else if (departure.routeStations.isNotEmpty()) {
            Section(copy.routeStops)
            departure.routeStations.forEach { station -> ListLabel(station) }
        }
        if (departure.messages.isNotEmpty()) {
            Section(copy.information)
            departure.messages.forEach { Notice(it) }
        }
    }
}

@Composable
private fun DisruptionsContent(mode: ScreenMode.Disruptions, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.disruptions, vm::home) {
        if (mode.disruptions.isEmpty()) Body(copy.noDisruptions)
        mode.disruptions.forEach { disruption -> DisruptionCard(disruption) { vm.showDisruption(disruption) } }
    }
}

@Composable
private fun DisruptionDetailsContent(disruption: Disruption, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.disruptions, vm::home) {
        DisruptionHero(disruption)
        disruption.trajectories.forEach { Notice(it) }
        val situation = listOfNotNull(disruption.cause, disruption.situation, disruption.description)
        if (situation.isNotEmpty()) {
            Section(copy.information)
            situation.forEach { Body(it) }
        }
        val advice = listOfNotNull(disruption.advice, disruption.expectedDuration, disruption.additionalTravelTime) + disruption.advices + disruption.consequences
        if (advice.isNotEmpty()) {
            Section(copy.guidance)
            advice.forEach { Body(it) }
        }
    }
}

@Composable
private fun PlannerContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.planner, vm::home) {
        Section(copy.route)
        PlannerRouteCard(state, copy, vm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            UtilityAction("⇅", copy.swap, Modifier.weight(1f), vm::swapPlannerStations)
            if (state.plannerVia != null) {
                UtilityAction("×", copy.removeVia, Modifier.weight(1f), vm::clearVia)
            }
        }
        Section(copy.whenToTravel)
        val preferences = state.persisted.plannerPreferences
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            ChoiceCard(
                if (preferences.timeMode == PlannerTimeMode.DEPARTURE) copy.leaving else copy.arriving,
                copy.whenToTravel,
                Modifier.weight(1f),
                vm::togglePlannerTimeMode,
            )
            ChoiceCard(
                preferences.dateTime?.let(::dateTime) ?: copy.now,
                copy.chooseTime,
                Modifier.weight(1f),
                vm::openDateTimeInput,
            )
        }
        if (preferences.dateTime != null) ListAction(copy.now, copy.chooseTime, vm::useCurrentTime)
        PrimaryAction(copy.plan, routeSummary(state.plannerOrigin, state.plannerDestination), Modifier.fillMaxWidth(), vm::planJourney)
    }
}

@Composable
private fun TripsContent(mode: ScreenMode.Trips, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(if (mode.recovery) copy.recovery else copy.planner, vm::openPlanner) {
        if (mode.trips.isEmpty()) Body(copy.noJourneys)
        mode.trips.forEachIndexed { index, trip ->
            JourneyResultCard(index + 1, trip, copy) { vm.showTrip(trip, mode.request) }
        }
    }
}

@Composable
private fun TripDetailsContent(mode: ScreenMode.TripDetails, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    val trip = mode.trip
    val active = state.persisted.activeJourney?.id == trip.id
    ScreenFrame(copy.journeyDetails, vm::openPlanner) {
        JourneyOverview(trip, copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            PrimaryAction(
                title = if (active) copy.openActiveJourney else copy.track,
                detail = "",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (active) vm.openActive() else vm.startTracking(trip)
                    Unit
                },
            )
            mode.request?.let { request ->
                PrimaryAction(copy.saveRoute, "", Modifier.weight(1f)) { vm.saveRoute(request) }
            }
        }
        Section(copy.journeyTimeline)
        JourneyTimeline(trip, copy)
        JourneyFares(trip, copy)
        if (trip.disruptions.isNotEmpty()) {
            Section(copy.disruptions)
            trip.disruptions.forEach { ActionRow(it.title, it.severity) { vm.showDisruption(it) } }
        }
        if (trip.legs.size > 1) {
            Section(copy.more)
            ActionRow(copy.recovery, trip.legs.first().destination.name) { vm.loadRecovery(trip) }
        }
    }
}

@Composable
private fun ActiveJourneyContent(journey: TripOption, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.activeJourney, vm::home) {
        JourneyOverview(journey, copy)
        PrimaryAction(copy.refresh, copy.live, Modifier.fillMaxWidth(), vm::manualRefreshActive)
        Section(copy.journeyTimeline)
        JourneyTimeline(journey, copy)
        if (journey.disruptions.isNotEmpty()) {
            Section(copy.disruptions)
            journey.disruptions.forEach { Body(it.title) }
        }
        if (journey.legs.size > 1) {
            Section(copy.more)
            ActionRow(copy.recovery, journey.legs.first().destination.name) { vm.loadRecovery(journey) }
        }
        ActionRow(copy.stopTracking, copy.activeJourney, vm::stopTracking)
    }
}

@Composable
private fun JourneyOverview(trip: TripOption, copy: Copy) {
    val firstLeg = trip.legs.firstOrNull()
    val lastLeg = trip.legs.lastOrNull()
    val origin = firstLeg?.origin?.name.orEmpty()
    val destination = lastLeg?.destination?.name.orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(0.75f.gridUnitsAsDp()),
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            JourneyEndpoint(
                time = time(trip.departure),
                delayMinutes = firstLeg?.departureDelayMinutes ?: 0,
                station = origin,
                align = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            LightText("→", LightTextVariant.Subheading, monospace = true)
            JourneyEndpoint(
                time = time(trip.arrival),
                delayMinutes = lastLeg?.arrivalDelayMinutes ?: 0,
                station = destination,
                align = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        LightText(
            tripSummary(trip, copy),
            LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 0.55f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun JourneyEndpoint(
    time: String,
    delayMinutes: Int,
    station: String,
    align: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TimeWithDelay(
            time = time,
            delayMinutes = delayMinutes,
            variant = LightTextVariant.Subheading,
            modifier = Modifier.fillMaxWidth(),
            align = align,
        )
        LightText(station, LightTextVariant.Detail, align = align, modifier = Modifier.fillMaxWidth(), maxLines = 2)
    }
}

@Composable
private fun TimeWithDelay(
    time: String,
    delayMinutes: Int,
    variant: LightTextVariant,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (align == TextAlign.End) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(time, variant, color = color, monospace = true, maxLines = 1)
        if (delayMinutes > 0) {
            BoldDelay("+$delayMinutes", color)
        }
    }
}

@Composable
private fun BoldDelay(
    text: String,
    color: androidx.compose.ui.graphics.Color? = null,
    prominent: Boolean = false,
) {
    val fontSize = if (prominent) 24f else 18f
    val lineHeight = if (prominent) 27f else 20f
    Text(
        text = text,
        color = color ?: LightThemeTokens.colors.content,
        maxLines = 1,
        style = TextStyle(
            fontSize = fontSize.designVerticalPxToSp(),
            lineHeight = lineHeight.designVerticalPxToSp(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun JourneyTimeRange(
    trip: TripOption,
    variant: LightTextVariant,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val departureDelay = trip.legs.firstOrNull()?.departureDelayMinutes ?: 0
    val arrivalDelay = trip.legs.lastOrNull()?.arrivalDelayMinutes ?: 0
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TimeWithDelay(time(trip.departure), departureDelay, variant, color = color)
        LightText("–", variant, color = color, monospace = true, maxLines = 1)
        TimeWithDelay(time(trip.arrival), arrivalDelay, variant, color = color)
    }
}

@Composable
private fun PrimaryAction(
    title: String,
    detail: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .height(1.8f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.content)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.45f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LightText(
                title,
                LightTextVariant.Paragraph,
                color = LightThemeTokens.colors.background,
                align = TextAlign.Center,
                maxLines = 2,
            )
            if (detail.isNotBlank()) {
                LightText(
                    detail,
                    LightTextVariant.Superfine,
                    color = LightThemeTokens.colors.background,
                    monospace = true,
                    align = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun JourneyTimeline(trip: TripOption, copy: Copy) {
    if (trip.legs.isEmpty()) {
        Body(copy.noJourneys)
        return
    }
    trip.legs.forEachIndexed { index, leg ->
        if (index == 0) {
            TimelineStation(
                label = copy.leaving,
                station = leg.origin.name,
                actualTime = leg.actualDeparture,
                plannedTime = leg.plannedDeparture,
                platform = leg.actualDeparturePlatform ?: leg.plannedDeparturePlatform,
                delayMinutes = leg.departureDelayMinutes,
                cancelled = leg.cancelled,
                copy = copy,
                connectBelow = true,
            )
        }
        TimelineRide(leg, copy)
        val nextLeg = trip.legs.getOrNull(index + 1)
        if (nextLeg == null) {
            TimelineStation(
                label = copy.arriving,
                station = leg.destination.name,
                actualTime = leg.actualArrival,
                plannedTime = leg.plannedArrival,
                platform = leg.actualArrivalPlatform ?: leg.plannedArrivalPlatform,
                delayMinutes = leg.arrivalDelayMinutes,
                cancelled = leg.cancelled,
                copy = copy,
                connectAbove = true,
            )
        } else {
            TimelineTransfer(leg, nextLeg, copy)
        }
    }
}

@Composable
private fun TimelineStation(
    label: String,
    station: String,
    actualTime: String,
    plannedTime: String,
    platform: String?,
    delayMinutes: Int,
    cancelled: Boolean,
    copy: Copy,
    markerInverted: Boolean = false,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineTime(time(actualTime), delayMinutes, Modifier.padding(top = 0.35f.gridUnitsAsDp()))
        TimelineRail(
            connectAbove = connectAbove,
            connectBelow = connectBelow,
            markerInverted = markerInverted,
            markerTopPadding = 0.35f,
        )
        Column(Modifier.weight(1f).padding(vertical = 0.35f.gridUnitsAsDp())) {
            LightText(station, LightTextVariant.ParagraphWide, maxLines = 2)
            Row(
                modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.spacedBy(0.3f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(label.uppercase(Locale.ROOT), LightTextVariant.Superfine, monospace = true, lighten = true)
                platform?.takeIf(String::isNotBlank)?.let { Badge("${copy.platform} $it", inverted = true) }
                if (cancelled) Badge(copy.cancelled, inverted = true)
            }
            if (delayMinutes > 0) {
                LightText(
                    "${copy.scheduled} ${time(plannedTime)}",
                    LightTextVariant.Superfine,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
private fun TimelineRide(leg: TripLeg, copy: Copy) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(timelineTimeColumnGridUnits.gridUnitsAsDp()))
        TimelineLine()
        Column(
            Modifier
                .weight(1f)
                .padding(start = 0.35f.gridUnitsAsDp(), top = 0.35f.gridUnitsAsDp(), bottom = 0.7f.gridUnitsAsDp()),
        ) {
            TrainPlatformBadges(leg.trainType, leg.trainNumber, null, copy)
            leg.serviceDestinationName?.takeIf(String::isNotBlank)?.let {
                LightText(
                    "${copy.towards} $it",
                    LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()),
                    maxLines = 2,
                )
            }
            leg.messages.forEach { message ->
                LightText(message, LightTextVariant.Detail, modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()), maxLines = 3)
            }
        }
    }
}

@Composable
private fun TimelineTransfer(arrivingLeg: TripLeg, departingLeg: TripLeg, copy: Copy) {
    val station = arrivingLeg.destination.name
    val arrivalPlatform = arrivingLeg.actualArrivalPlatform ?: arrivingLeg.plannedArrivalPlatform
    val departurePlatform = departingLeg.actualDeparturePlatform ?: departingLeg.plannedDeparturePlatform

    TimelineStation(
        label = copy.arriving,
        station = station,
        actualTime = arrivingLeg.actualArrival,
        plannedTime = arrivingLeg.plannedArrival,
        platform = arrivalPlatform,
        delayMinutes = arrivingLeg.arrivalDelayMinutes,
        cancelled = arrivingLeg.cancelled,
        copy = copy,
        markerInverted = true,
        connectAbove = true,
        connectBelow = true,
    )
    TimelineTransferWait(
        durationMinutes = transferMinutes(arrivingLeg, departingLeg),
        arrivalPlatform = arrivalPlatform,
        departurePlatform = departurePlatform,
        copy = copy,
    )
    TimelineStation(
        label = copy.leaving,
        station = station,
        actualTime = departingLeg.actualDeparture,
        plannedTime = departingLeg.plannedDeparture,
        platform = departurePlatform,
        delayMinutes = departingLeg.departureDelayMinutes,
        cancelled = departingLeg.cancelled,
        copy = copy,
        connectAbove = true,
        connectBelow = true,
    )
}

@Composable
private fun TimelineTransferWait(
    durationMinutes: Int?,
    arrivalPlatform: String?,
    departurePlatform: String?,
    copy: Copy,
) {
    val duration = durationMinutes?.let { "$it ${copy.minutes.uppercase(Locale.ROOT)} " }.orEmpty()
    val platformChange = when {
        !arrivalPlatform.isNullOrBlank() && !departurePlatform.isNullOrBlank() && arrivalPlatform != departurePlatform ->
            "${copy.platform} $arrivalPlatform → ${copy.platform} $departurePlatform"
        !departurePlatform.isNullOrBlank() -> "${copy.platform} $departurePlatform"
        !arrivalPlatform.isNullOrBlank() -> "${copy.platform} $arrivalPlatform"
        else -> ""
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(timelineTimeColumnGridUnits.gridUnitsAsDp()))
        TimelineRail(true, true, false, 0.55f)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 0.35f.gridUnitsAsDp(), top = 0.35f.gridUnitsAsDp(), bottom = 0.35f.gridUnitsAsDp())
                .background(LightThemeTokens.colors.content)
                .padding(horizontal = 0.6f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp()),
        ) {
            LightText(
                "$duration${copy.transfer.uppercase(Locale.ROOT)}",
                LightTextVariant.Paragraph,
                color = LightThemeTokens.colors.background,
                monospace = true,
            )
            if (platformChange.isNotBlank()) {
                LightText(
                    platformChange,
                    LightTextVariant.Detail,
                    color = LightThemeTokens.colors.background,
                    modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp()),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TimelineTime(value: String, delayMinutes: Int = 0, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(timelineTimeColumnGridUnits.gridUnitsAsDp()).padding(end = 0.25f.gridUnitsAsDp()),
        horizontalAlignment = Alignment.End,
    ) {
        LightText(value, LightTextVariant.ParagraphWide, monospace = true, maxLines = 1)
        if (delayMinutes > 0) BoldDelay("+$delayMinutes", prominent = true)
    }
}

@Composable
private fun TimelineRail(
    connectAbove: Boolean,
    connectBelow: Boolean,
    markerInverted: Boolean,
    markerTopPadding: Float,
) {
    val markerLineCentre = markerTopPadding + 0.45f
    Box(Modifier.width(0.75f.gridUnitsAsDp()).fillMaxHeight()) {
        if (connectAbove) {
            Spacer(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(2f.designVerticalPxToDp())
                    .height(markerLineCentre.gridUnitsAsDp())
                    .background(LightThemeTokens.colors.content),
            )
        }
        if (connectBelow) {
            Spacer(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = markerLineCentre.gridUnitsAsDp())
                    .width(2f.designVerticalPxToDp())
                    .fillMaxHeight()
                    .background(LightThemeTokens.colors.content),
            )
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = markerTopPadding.gridUnitsAsDp())
                .width(0.75f.gridUnitsAsDp())
                .height(0.9f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(0.34f.gridUnitsAsDp())
                    .height(0.34f.gridUnitsAsDp())
                    .background(if (markerInverted) LightThemeTokens.colors.background else LightThemeTokens.colors.content)
                    .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content),
            )
        }
    }
}

@Composable
private fun TimelineLine() {
    Box(
        Modifier
            .width(0.75f.gridUnitsAsDp())
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .width(2f.designVerticalPxToDp())
                .fillMaxHeight()
                .background(LightThemeTokens.colors.content),
        )
    }
}

@Composable
private fun JourneyFares(trip: TripOption, copy: Copy) {
    if (trip.price == null && trip.firstClassPrice == null && trip.supplementPrice == null) return
    Section(copy.fares)
    trip.price?.let { Detail(copy.secondClass, price(it)) }
    trip.firstClassPrice?.let { Detail(copy.firstClass, price(it)) }
    trip.supplementPrice?.let { Detail(copy.supplement, price(it)) }
}

@Composable
private fun FavouritesContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.favourites, vm::home) {
        Section(copy.favouriteStations)
        if (state.persisted.favouriteStations.isEmpty()) Body(copy.noFavouriteStations)
        state.persisted.favouriteStations.forEach { station -> ListAction(station.name, copy.departures) { vm.loadDepartures(station) } }
        Section(copy.favouriteRoutes)
        if (state.persisted.favouriteRoutes.isEmpty()) Body(copy.noFavouriteRoutes)
        state.persisted.favouriteRoutes.forEach { route ->
            FavouriteRouteCard(route, copy, vm)
        }
    }
}

@Composable
private fun SettingsContent(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.settings, vm::home) {
        Section("Language / Taal")
        SegmentedChoices(
            left = copy.english,
            right = copy.dutch,
            leftSelected = state.persisted.language == Language.ENGLISH,
            onLeft = { vm.setLanguage(Language.ENGLISH) },
            onRight = { vm.setLanguage(Language.DUTCH) },
        )
        Section(copy.notifications)
        SettingToggle(copy.alerts, state.persisted.notificationPreferences.alertsEnabled, copy, vm::toggleAlerts)
        SettingToggle(copy.guidance, state.persisted.notificationPreferences.guidanceEnabled, copy, vm::toggleGuidance)
        Notice(copy.notificationsLimited)
        Section(copy.connection)
        StatusPanel(
            copy.connection,
            if (BuildConfig.WORKER_ACCESS_TOKEN.isBlank()) copy.notConfigured else copy.configured,
            connected = BuildConfig.WORKER_ACCESS_TOKEN.isNotBlank(),
        )
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
private fun LiveJourneyCard(journey: TripOption, copy: Copy, onClick: () -> Unit) {
    val origin = journey.legs.firstOrNull()?.origin?.name.orEmpty()
    val destination = journey.legs.lastOrNull()?.destination?.name.orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 0.55f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.8f.gridUnitsAsDp()),
    ) {
        LightText(
            copy.liveJourney.uppercase(Locale.ROOT),
            LightTextVariant.Superfine,
            color = LightThemeTokens.colors.background,
            monospace = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 0.25f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                "$origin → $destination",
                LightTextVariant.Subheading,
                color = LightThemeTokens.colors.background,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            LightText("→", LightTextVariant.Subheading, color = LightThemeTokens.colors.background, monospace = true)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 0.25f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JourneyTimeRange(
                trip = journey,
                variant = LightTextVariant.Detail,
                color = LightThemeTokens.colors.background,
            )
            LightText(
                " · ${tripSummary(journey, copy)}",
                LightTextVariant.Detail,
                color = LightThemeTokens.colors.background,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeShortcut(index: String, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .padding(vertical = 0.25f.gridUnitsAsDp())
            .height(3.15f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.55f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LightText(index, LightTextVariant.Superfine, monospace = true, lighten = true)
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
        LightText(title, LightTextVariant.ParagraphWide, maxLines = 2)
    }
}

@Composable
private fun ListAction(title: String, detail: String = "", onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.35f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LightText(title, LightTextVariant.ParagraphWide, maxLines = 2)
                if (detail.isNotBlank()) LightText(detail, LightTextVariant.Detail, lighten = true, maxLines = 2)
            }
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(top = 0.45f.gridUnitsAsDp())
                .height(1f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.contentSecondary),
        )
    }
}

@Composable
private fun ListLabel(title: String, detail: String = "") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 0.35f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp())) {
        LightText(title, LightTextVariant.ParagraphWide, maxLines = 2)
        if (detail.isNotBlank()) LightText(detail, LightTextVariant.Detail, lighten = true, maxLines = 2)
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(top = 0.4f.gridUnitsAsDp())
                .height(1f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.contentSecondary),
        )
    }
}

@Composable
private fun PlannerRouteCard(state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    Column(Modifier.fillMaxWidth().border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)) {
        PlannerField("A", copy.origin, state.plannerOrigin?.name ?: copy.chooseStation, divider = true) {
            vm.openStationSearch(StationPurpose.ORIGIN)
        }
        PlannerField("B", copy.destination, state.plannerDestination?.name ?: copy.chooseStation, divider = true) {
            vm.openStationSearch(StationPurpose.DESTINATION)
        }
        PlannerField("V", copy.via, state.plannerVia?.name ?: copy.chooseStation, divider = false) {
            vm.openStationSearch(StationPurpose.VIA)
        }
    }
}

@Composable
private fun PlannerField(marker: String, label: String, value: String, divider: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().lightClickable(onClick = onClick).padding(horizontal = 0.7f.gridUnitsAsDp())) {
        Row(Modifier.fillMaxWidth().padding(vertical = 0.55f.gridUnitsAsDp()), verticalAlignment = Alignment.CenterVertically) {
            Badge(marker, inverted = true)
            Column(Modifier.weight(1f).padding(start = 0.6f.gridUnitsAsDp())) {
                LightText(label.uppercase(Locale.ROOT), LightTextVariant.Superfine, monospace = true, lighten = true)
                LightText(value, LightTextVariant.ParagraphWide, maxLines = 2)
            }
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
        if (divider) Spacer(Modifier.fillMaxWidth().height(1f.designVerticalPxToDp()).background(LightThemeTokens.colors.contentSecondary))
    }
}

@Composable
private fun ChoiceCard(title: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .padding(vertical = 0.25f.gridUnitsAsDp())
            .height(2.85f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.55f.gridUnitsAsDp()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        LightText(label.uppercase(Locale.ROOT), LightTextVariant.Superfine, monospace = true, lighten = true, maxLines = 1)
        LightText(title, LightTextVariant.ParagraphWide, maxLines = 2)
    }
}

@Composable
private fun JourneyResultCard(index: Int, trip: TripOption, copy: Copy, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Badge(index.toString().padStart(2, '0'), inverted = true)
            JourneyTimeRange(
                trip = trip,
                variant = LightTextVariant.Subheading,
                modifier = Modifier.weight(1f).padding(start = 0.65f.gridUnitsAsDp()),
            )
            LightText("→", LightTextVariant.Subheading, monospace = true)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 0.45f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText("${trip.durationMinutes} ${copy.minutes}", LightTextVariant.Detail, lighten = true)
            Badge(if (trip.transfers == 0) copy.direct else "${trip.transfers} ${if (trip.transfers == 1) copy.transfer else copy.transfers}")
        }
    }
}

@Composable
private fun DepartureHero(departure: Departure, copy: Copy) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(0.8f.gridUnitsAsDp()),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LightText(time(departure.actualDateTime), LightTextVariant.Subheading, monospace = true)
            LightText(departure.direction, LightTextVariant.Subheading, align = TextAlign.End, modifier = Modifier.weight(1f), maxLines = 2)
        }
        TrainPlatformBadges(departure.trainType, departure.trainNumber, departure.actualTrack ?: departure.plannedTrack, copy)
        departureStatus(departure, copy)?.let { status ->
            LightText(status, LightTextVariant.Detail, modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()))
        }
        LightText(dateTime(departure.actualDateTime), LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()))
    }
}

@Composable
private fun DepartureStopRow(stop: DepartureStop, first: Boolean, last: Boolean, copy: Copy) {
    val value = stop.actualDeparture ?: stop.actualArrival ?: stop.plannedDeparture ?: stop.plannedArrival.orEmpty()
    val platform = stop.actualPlatform ?: stop.plannedPlatform
    Row(Modifier.fillMaxWidth().padding(vertical = 0.35f.gridUnitsAsDp()), verticalAlignment = Alignment.Top) {
        TimelineTime(value.takeIf(String::isNotBlank)?.let(::time).orEmpty())
        Box(Modifier.width(0.75f.gridUnitsAsDp()), contentAlignment = Alignment.TopCenter) {
            LightText(if (first || last) "■" else "□", LightTextVariant.Superfine, monospace = true)
        }
        Column(Modifier.weight(1f)) {
            LightText(stop.name, LightTextVariant.ParagraphWide, maxLines = 2)
            platform?.takeIf(String::isNotBlank)?.let { Badge("${copy.platform} $it", inverted = true) }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 0.45f.gridUnitsAsDp())
                    .height(1f.designVerticalPxToDp())
                    .background(LightThemeTokens.colors.contentSecondary),
            )
        }
    }
}

@Composable
private fun DisruptionCard(disruption: Disruption, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Badge(disruption.severity, inverted = true)
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
        LightText(disruption.title, LightTextVariant.ParagraphWide, modifier = Modifier.padding(top = 0.4f.gridUnitsAsDp()), maxLines = 3)
        disruption.trajectories.firstOrNull()?.let {
            LightText(it, LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 2)
        }
    }
}

@Composable
private fun DisruptionHero(disruption: Disruption) {
    Column(Modifier.fillMaxWidth().background(LightThemeTokens.colors.content).padding(0.8f.gridUnitsAsDp())) {
        LightText(disruption.severity.uppercase(Locale.ROOT), LightTextVariant.Superfine, color = LightThemeTokens.colors.background, monospace = true)
        LightText(disruption.title, LightTextVariant.Subheading, color = LightThemeTokens.colors.background, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 4)
        LightText(disruption.type, LightTextVariant.Detail, color = LightThemeTokens.colors.background, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 2)
    }
}

@Composable
private fun Notice(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 0.4f.gridUnitsAsDp()).height(IntrinsicSize.Min)) {
        Spacer(Modifier.width(4f.designVerticalPxToDp()).fillMaxHeight().background(LightThemeTokens.colors.content))
        LightText(text, LightTextVariant.Paragraph, modifier = Modifier.padding(start = 0.65f.gridUnitsAsDp()), maxLines = 8)
    }
}

@Composable
private fun FavouriteRouteCard(route: FavouriteRoute, copy: Copy, vm: TreinwijzerViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(0.7f.gridUnitsAsDp()),
    ) {
        LightText(route.name, LightTextVariant.ParagraphWide, maxLines = 2)
        route.via?.let { LightText("${copy.via} ${it.name}", LightTextVariant.Detail, lighten = true, maxLines = 2) }
        Row(
            Modifier.fillMaxWidth().padding(top = 0.35f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            PrimaryAction(copy.useRoute, "", Modifier.weight(1f)) { vm.useRoute(route) }
            UtilityAction("×", copy.removeFavourite, Modifier.weight(1f)) { vm.removeRoute(route) }
        }
    }
}

@Composable
private fun SegmentedChoices(
    left: String,
    right: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp())) {
        SegmentChoice(left, leftSelected, Modifier.weight(1f), onLeft)
        SegmentChoice(right, !leftSelected, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun SegmentChoice(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background = if (selected) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (selected) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Box(
        modifier
            .height(1.7f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(title, LightTextVariant.Paragraph, color = foreground, align = TextAlign.Center)
    }
}

@Composable
private fun SettingToggle(title: String, enabled: Boolean, copy: Copy, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.25f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(title, LightTextVariant.ParagraphWide, modifier = Modifier.weight(1f), maxLines = 2)
        Badge(if (enabled) copy.enabled else copy.disabled, inverted = enabled)
    }
}

@Composable
private fun StatusPanel(title: String, detail: String, connected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(0.7f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            LightText(title, LightTextVariant.ParagraphWide)
            LightText(detail, LightTextVariant.Detail, lighten = true, maxLines = 3)
        }
        Badge(if (connected) "OK" else "—", inverted = connected)
    }
}

@Composable
private fun MessageScreen(title: String, message: String, onBack: (() -> Unit)? = null) {
    ScreenFrame(title, onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.5f.gridUnitsAsDp())
                .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
                .padding(1.25f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LightText("—", LightTextVariant.Title, monospace = true)
                LightText(message, LightTextVariant.Paragraph, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PickerShortcut(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(vertical = 0.18f.gridUnitsAsDp())
            .height(1.8f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.content)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.25f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            title,
            LightTextVariant.Paragraph,
            color = LightThemeTokens.colors.background,
            align = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun MenuAction(
    index: String,
    title: String,
    detail: String,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (prominent) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (prominent) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(index, inverted = !prominent)
        Column(Modifier.weight(1f).padding(horizontal = 0.7f.gridUnitsAsDp())) {
            LightText(title, LightTextVariant.Subheading, color = foreground, maxLines = 1)
            LightText(detail, LightTextVariant.Detail, color = foreground, lighten = !prominent, maxLines = 2)
        }
        LightText("→", LightTextVariant.Subheading, color = foreground, monospace = true)
    }
}

@Composable
private fun UtilityAction(marker: String, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.6f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(marker, LightTextVariant.Subheading, monospace = true)
        LightText(
            title,
            LightTextVariant.Paragraph,
            modifier = Modifier.weight(1f).padding(start = 0.45f.gridUnitsAsDp()),
            maxLines = 2,
        )
    }
}

@Composable
private fun LetterButton(letter: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .padding(vertical = 0.18f.gridUnitsAsDp())
            .height(1.15f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(letter, LightTextVariant.Subheading, monospace = true)
    }
}

@Composable
private fun ActionRow(title: String, detail: String = "", onClick: () -> Unit) =
    ActionRow(title, detail, prominent = false, onClick = onClick)

@Composable
private fun ActionRow(title: String, detail: String = "", prominent: Boolean, onClick: () -> Unit) {
    val background = if (prominent) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (prominent) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.65f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(title, LightTextVariant.ParagraphWide, color = foreground, modifier = Modifier.weight(1.15f), maxLines = 2)
        if (detail.isNotBlank()) {
            LightText(
                detail,
                LightTextVariant.Detail,
                lighten = !prominent,
                color = if (prominent) foreground else null,
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

private const val timelineTimeColumnGridUnits = 3.5f
private val stationLetters = ('A'..'Z').map { it.toString() }
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
private val compactOffsetFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")
private val amsterdam = ZoneId.of("Europe/Amsterdam")
private fun parsedInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, compactOffsetFormatter).toInstant() }.getOrNull()
private fun transferMinutes(arrivingLeg: TripLeg, departingLeg: TripLeg): Int? {
    arrivingLeg.transferMinutesAfterLeg?.let { return it }
    val arrival = parsedInstant(arrivingLeg.actualArrival) ?: return null
    val departure = parsedInstant(departingLeg.actualDeparture) ?: return null
    return Duration.between(arrival, departure).toMinutes().toInt().takeIf { it >= 0 }
}
internal fun time(value: String): String = parsedInstant(value)?.atZone(amsterdam)?.format(timeFormatter) ?: value
internal fun dateTime(value: String): String = parsedInstant(value)?.atZone(amsterdam)?.format(dateFormatter) ?: value
private fun price(value: JourneyPrice): String = String.format(Locale.UK, "€ %.2f", value.amountEuroCents / 100.0)
private fun journeyTitle(journey: TripOption): String = "${journey.legs.firstOrNull()?.origin?.name.orEmpty()} → ${journey.legs.lastOrNull()?.destination?.name.orEmpty()}"
private fun routeSummary(from: Station?, to: Station?): String = listOfNotNull(from?.name, to?.name).joinToString(" → ")
private fun tripSummary(trip: TripOption, copy: Copy): String {
    val transferText = "${trip.transfers} ${if (trip.transfers == 1) copy.transfer else copy.transfers}"
    return "${trip.durationMinutes} ${copy.minutes} · $transferText"
}
private fun departureStatus(departure: Departure, copy: Copy): String? = when {
    departure.cancelled -> copy.cancelled
    departure.delayMinutes > 0 -> "+${departure.delayMinutes} ${copy.minutes}"
    else -> null
}
