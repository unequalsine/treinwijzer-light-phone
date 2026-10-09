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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.callRemoteServiceMethod
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod
import com.thelightphone.sdk.shared.getOrNull
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
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
    private var resumeCount by mutableIntStateOf(0)

    override fun willShow() {
        resumeCount++
    }

    @Composable
    override fun Content() {
        val colours by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val copy = Copy(state.persisted.language)

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
                    is ScreenMode.StationIndex -> StationIndexContent(
                        mode,
                        state.persisted.stations,
                        copy,
                        viewModel,
                    )
                    is ScreenMode.StationResults -> StationResultsContent(mode, copy, viewModel)
                    is ScreenMode.StationRecents -> StationRecentsContent(mode, copy, viewModel)
                    is ScreenMode.StationNearest -> NearestStationsContent(mode, copy, viewModel, resumeCount)
                    is ScreenMode.Departures -> DeparturesContent(mode, state, copy, viewModel)
                    is ScreenMode.DepartureDetails -> DepartureDetailsContent(mode, copy, viewModel)
                    is ScreenMode.Disruptions -> DisruptionsContent(mode, copy, viewModel)
                    is ScreenMode.DisruptionDetails -> DisruptionDetailsContent(mode.disruption, copy, viewModel)
                    ScreenMode.Planner -> PlannerContent(state, copy, viewModel)
                    is ScreenMode.PlannerOptions -> PlannerOptionsContent(
                        state,
                        mode.selection,
                        mode.timeMode,
                        copy,
                        viewModel,
                    )
                    is ScreenMode.Trips -> TripsContent(mode, copy, viewModel)
                    is ScreenMode.TripDetails -> TripDetailsContent(mode, state, copy, viewModel)
                    ScreenMode.Favourites -> FavouritesContent(state, copy, viewModel)
                    ScreenMode.Settings -> SettingsContent(state, copy, viewModel)
                    ScreenMode.Privacy -> ScreenFrame(copy.privacy, if (state.persisted.privacyAccepted) viewModel::back else null) {
                        Body(copy.privacyNotice)
                        if (!state.persisted.privacyAccepted) ListAction(copy.continueLabel) { viewModel.acceptPrivacy() }
                    }
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
private fun NearestStationsContent(mode: ScreenMode.StationNearest, copy: Copy, vm: TreinwijzerViewModel, resumeCount: Int) {
    val permission = "android.permission.ACCESS_FINE_LOCATION"
    val permissionLauncher = rememberPermissionRequestLauncher(permission)
    var granted by remember(mode) { mutableStateOf(false) }
    var searching by remember(mode) { mutableStateOf(true) }
    var attempts by remember(mode) { mutableIntStateOf(0) }

    LaunchedEffect(mode, resumeCount, attempts) {
        searching = true
        granted = checkPermission(permission).getOrNull()?.permissionResult == LightServiceMethod.GetPermission.Result.Granted
        if (!granted) {
            searching = false
            return@LaunchedEffect
        }
        try {
            callRemoteServiceMethod(LightServiceMethod.RequestLocationUpdates, Unit)
            repeat(12) {
                val location = callRemoteServiceMethod(LightServiceMethod.GetCurrentLocation, Unit).getOrNull()
                if (usableLocation(location?.latitude, location?.longitude, location?.accuracyMeters, location?.timestampMs, System.currentTimeMillis())) {
                    vm.selectNearestStations(mode, location!!.latitude!!, location.longitude!!)
                    return@LaunchedEffect
                }
                delay(2_000)
            }
            searching = false
        } finally {
            withContext(NonCancellable) {
                callRemoteServiceMethod(LightServiceMethod.ReleaseLocationUpdates, Unit)
            }
        }
    }

    ScreenFrame(copy.nearest, vm::back) {
        when {
            searching -> Body(copy.loading)
            !granted -> {
                Body(copy.locationPermission)
                ListAction(copy.allowLocation) { permissionLauncher?.launch() }
            }
            else -> {
                Body(copy.nearestUnavailable)
                ListAction(copy.retry) { attempts++ }
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
        MenuAction(
            LightIcons.MAP,
            copy.planner,
            prominent = true,
            onClick = vm::openPlanner,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            HomeShortcut(LightIcons.DIRECTIONS_TRAIN, copy.departures, Modifier.weight(1f)) {
                vm.openStationSearch(StationPurpose.INFO)
            }
            HomeShortcut(LightIcons.EMERGENCY, copy.disruptions, Modifier.weight(1f)) {
                vm.openStationSearch(StationPurpose.DISRUPTIONS)
            }
        }
        if (state.persisted.favouriteStations.isNotEmpty()) {
            Section(copy.favouriteStations)
            state.persisted.favouriteStations.take(4).forEach { station ->
                ListAction(station.name) { vm.loadDepartures(station) }
            }
        }
        Section(copy.more)
        ListAction(copy.manageFavourites, onClick = vm::openFavourites)
        ListAction(copy.settings, onClick = vm::openSettings)
    }
}

@Composable
private fun StationPickerContent(
    mode: ScreenMode.StationPicker,
    state: TreinwijzerUiState,
    copy: Copy,
    vm: TreinwijzerViewModel,
) {
    ScreenFrame(copy.chooseStation, vm::back) {
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
        if (mode.purpose == StationPurpose.VIA && state.plannerVia != null) {
            ActionRow(copy.removeVia, state.plannerVia.name) {
                vm.clearVia()
                vm.back()
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
private fun StationIndexContent(
    mode: ScreenMode.StationIndex,
    stations: List<Station>,
    copy: Copy,
    vm: TreinwijzerViewModel,
) {
    val availableLetters = TreinwijzerViewModel.availableStationIndexLetters(stations)
    ScreenFrame(copy.stationIndex, vm::back) {
        Section(copy.chooseLetter)
        if (availableLetters.isEmpty()) Body(copy.noResults)
        availableLetters.chunked(3).forEach { rowLetters ->
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
    ScreenFrame(mode.query, vm::back) {
        if (mode.stations.isEmpty()) Body(copy.noResults)
        mode.stations.forEach { station -> ListAction(station.name) { vm.selectStation(station, mode.purpose) } }
    }
}

@Composable
private fun StationRecentsContent(mode: ScreenMode.StationRecents, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.recents, vm::back) {
        if (mode.stations.isEmpty()) Body(copy.noResults)
        mode.stations.forEach { station -> ListAction(station.name) { vm.selectStation(station, mode.purpose) } }
    }
}

@Composable
private fun DeparturesContent(mode: ScreenMode.Departures, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(mode.station.name, vm::back) {
        val favourite = state.persisted.favouriteStations.any { it.code == mode.station.code }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            UtilityAction(
                title = if (favourite) copy.removeFavourite else copy.addFavourite,
                modifier = Modifier.weight(1f),
                marker = if (favourite) "−" else "+",
            ) {
                vm.toggleFavouriteStation(mode.station)
            }
            UtilityAction(
                title = copy.disruptions,
                modifier = Modifier.weight(1f),
                icon = LightIcons.EMERGENCY,
            ) {
                vm.loadDisruptions(mode.station)
            }
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
    ScreenFrame(copy.departureDetails, vm::back) {
        DepartureHero(departure, copy)
        if (departure.stops.isNotEmpty()) {
            Section(copy.routeStops)
            departure.stops.forEachIndexed { index, stop ->
                DepartureStopRow(stop, first = index == 0, last = index == departure.stops.lastIndex, copy = copy)
            }
        } else if (departure.routeStations.isNotEmpty()) {
            Section(copy.routeStops)
            departure.routeStations.forEachIndexed { index, station ->
                DepartureRouteStationRow(
                    station = station,
                    first = index == 0,
                    last = index == departure.routeStations.lastIndex,
                    copy = copy,
                )
            }
        }
        if (departure.messages.isNotEmpty()) {
            Section(copy.information)
            departure.messages.forEach { Notice(it) }
        }
    }
}

@Composable
private fun DisruptionsContent(mode: ScreenMode.Disruptions, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(copy.disruptions, vm::back) {
        val currentDisruptions = mode.disruptions.filterNot(Disruption::isMaintenance)
        val plannedMaintenance = mode.disruptions.filter(Disruption::isMaintenance)
        if (mode.disruptions.isEmpty()) Body(copy.noDisruptions)
        if (currentDisruptions.isNotEmpty()) {
            Section(copy.currentDisruptions)
            currentDisruptions.forEach { disruption ->
                DisruptionCard(disruption, copy) { vm.showDisruption(disruption) }
            }
        }
        if (plannedMaintenance.isNotEmpty()) {
            Section(copy.plannedMaintenance)
            plannedMaintenance.forEach { disruption ->
                DisruptionCard(disruption, copy) { vm.showDisruption(disruption) }
            }
        }
    }
}

@Composable
private fun DisruptionDetailsContent(disruption: Disruption, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(if (disruption.isMaintenance) copy.plannedMaintenance else copy.disruption, vm::back) {
        DisruptionHero(disruption, copy)
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
    ScreenFrame(copy.planner, vm::back) {
        Section(copy.route)
        PlannerRouteCard(state, copy, vm)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 0.45f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            PlannerAction(copy.swap, prominent = false, modifier = Modifier.weight(1f), onClick = vm::swapPlannerStations)
            PlannerAction(copy.options, prominent = false, modifier = Modifier.weight(1f), onClick = vm::openPlannerOptions)
            PlannerAction(copy.plan, prominent = true, modifier = Modifier.weight(1f), onClick = vm::planJourney)
        }
    }
}

@Composable
private fun PlannerOptionsContent(
    state: TreinwijzerUiState,
    selection: PlannerDateTimeSelection,
    timeMode: PlannerTimeMode,
    copy: Copy,
    vm: TreinwijzerViewModel,
) {
    val selectedDay = if (selection.dayOffset == 0) copy.today else copy.tomorrow
    val selectedTime = "%02d:%02d".format(Locale.ROOT, selection.hour, selection.minute)
    ScreenFrame(copy.options, vm::back) {
        Column(Modifier.fillMaxWidth().border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)) {
            PlannerField("V", copy.via, state.plannerVia?.name ?: copy.chooseStation, divider = false) {
                vm.openStationSearch(StationPurpose.VIA)
            }
        }
        Section(copy.planBy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            SegmentChoice(copy.leaveNow, false, Modifier.weight(1f), vm::useCurrentTime)
            SegmentChoice(
                copy.departBy,
                selected = timeMode == PlannerTimeMode.DEPARTURE,
                modifier = Modifier.weight(1f),
            ) { vm.setPlannerTimeMode(PlannerTimeMode.DEPARTURE) }
            SegmentChoice(
                copy.arriveAt,
                selected = timeMode == PlannerTimeMode.ARRIVAL,
                modifier = Modifier.weight(1f),
            ) { vm.setPlannerTimeMode(PlannerTimeMode.ARRIVAL) }
        }
        Section(copy.day)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            SegmentChoice(copy.today, selection.dayOffset == 0, Modifier.weight(1f)) { vm.selectPlannerDay(0) }
            SegmentChoice(copy.tomorrow, selection.dayOffset == 1, Modifier.weight(1f)) { vm.selectPlannerDay(1) }
        }
        Section(copy.time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
            verticalAlignment = Alignment.Bottom,
        ) {
            TimeStepper(
                label = copy.hour,
                value = selection.hour.toString().padStart(2, '0'),
                modifier = Modifier.weight(1f),
                onDecrease = { vm.adjustPlannerTime(-60) },
                onIncrease = { vm.adjustPlannerTime(60) },
            )
            LightText(
                ":",
                LightTextVariant.Heading,
                monospace = true,
                modifier = Modifier.padding(bottom = 0.35f.gridUnitsAsDp()),
            )
            TimeStepper(
                label = copy.minute,
                value = selection.minute.toString().padStart(2, '0'),
                modifier = Modifier.weight(1f),
                onDecrease = { vm.adjustPlannerTime(-5) },
                onIncrease = { vm.adjustPlannerTime(5) },
            )
        }
        LightText(
            "$selectedDay · $selectedTime",
            LightTextVariant.Detail,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 0.65f.gridUnitsAsDp()),
        )
        PlannerAction(
            copy.useOptions,
            prominent = true,
            modifier = Modifier.fillMaxWidth().padding(top = 0.2f.gridUnitsAsDp()),
            onClick = vm::confirmPlannerDateTime,
        )
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(modifier) {
        LightText(
            label.uppercase(Locale.ROOT),
            LightTextVariant.Superfine,
            monospace = true,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.2f.gridUnitsAsDp()),
        ) {
            TimeAdjustButton("−", Modifier.weight(1f), onDecrease)
            Box(
                Modifier
                    .weight(1.4f)
                    .height(1.7f.gridUnitsAsDp())
                    .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content),
                contentAlignment = Alignment.Center,
            ) {
                LightText(value, LightTextVariant.Subheading, monospace = true)
            }
            TimeAdjustButton("+", Modifier.weight(1f), onIncrease)
        }
    }
}

@Composable
private fun TimeAdjustButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(1.7f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.content)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            label,
            LightTextVariant.Subheading,
            color = LightThemeTokens.colors.background,
            monospace = true,
        )
    }
}

@Composable
private fun TripsContent(mode: ScreenMode.Trips, copy: Copy, vm: TreinwijzerViewModel) {
    ScreenFrame(if (mode.recovery) copy.recovery else copy.planner, vm::back) {
        if (mode.trips.isEmpty()) Body(copy.noJourneys)
        mode.trips.forEach { trip ->
            JourneyResultCard(trip, copy) { vm.showTrip(trip, mode.request) }
        }
    }
}

@Composable
private fun TripDetailsContent(mode: ScreenMode.TripDetails, state: TreinwijzerUiState, copy: Copy, vm: TreinwijzerViewModel) {
    val trip = mode.trip
    val active = state.persisted.activeJourney?.id == trip.id
    ScreenFrame(copy.journeyDetails, vm::back) {
        JourneyOverview(trip, copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        ) {
            mode.request?.let { request ->
                PrimaryAction(copy.saveRoute, "", Modifier.weight(1f)) { vm.saveRoute(request) }
            }
            PrimaryAction(
                title = if (active) copy.openActiveJourney else copy.track,
                detail = "",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (active) vm.openActive() else vm.startTracking(trip)
                    Unit
                },
            )
        }
        Section(copy.journeyTimeline)
        JourneyTimeline(trip, copy)
        JourneyFares(trip, copy)
        if (trip.disruptions.isNotEmpty()) {
            Section(copy.disruptions)
            trip.disruptions.forEach { disruption ->
                ActionRow(disruption.title, disruptionCategory(disruption, copy)) {
                    vm.showDisruption(disruption)
                }
            }
        }
    }
}

@Composable
private fun ActiveJourneyContent(journey: TripOption, copy: Copy, vm: TreinwijzerViewModel) {
    val now = Instant.now()
    val recoveryRoute = journey.activeRecoveryRoute(now)
    val scrollState = rememberScrollState()
    
    Column(Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = vm::back),
            center = LightTopBarCenter.Text(copy.activeJourney),
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(2f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.content),
        )
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            scrollState = scrollState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
            ) {
                JourneyOverview(journey, copy)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
                ) {
                    recoveryRoute?.let { route ->
                        SecondaryAction(copy.recover, Modifier.weight(1f)) { vm.loadRecovery(route) }
                    }
                    SecondaryAction(copy.stop, Modifier.weight(1f), vm::stopTracking)
                    PrimaryAction(copy.refresh, modifier = Modifier.weight(1f), onClick = vm::manualRefreshActive)
                }
                Section(copy.journeyTimeline)
                JourneyTimeline(journey, copy, now, scrollState)
                JourneyFares(journey, copy)
                if (journey.disruptions.isNotEmpty()) {
                    Section(copy.disruptions)
                    journey.disruptions.forEach { disruption ->
                        ActionRow(disruption.title, disruptionCategory(disruption, copy)) {
                            vm.showDisruption(disruption)
                        }
                    }
                }
                Spacer(Modifier.height(2f.gridUnitsAsDp()))
            }
        }

        // Auto-scroll to current element
        ActiveJourneyAutoScroll(journey, now, scrollState)
    }
}

@Composable
private fun ActiveJourneyAutoScroll(journey: TripOption, now: Instant, scrollState: ScrollState) {
    val firstNonPastIndex = remember(journey, now) {
        findFirstNonPastTimelineIndex(journey, now)
    }
    
    LaunchedEffect(firstNonPastIndex, scrollState.maxValue) {
        if (firstNonPastIndex > 0 && scrollState.maxValue > 0) {
            delay(100) // Small delay to allow UI to compose
            // Estimate position based on index
            val target = scrollState.maxValue * firstNonPastIndex / (journey.legs.size * 2).coerceAtLeast(1)
            scrollState.scrollTo(target)
        }
    }
}

private fun findFirstNonPastTimelineIndex(journey: TripOption, now: Instant): Int {
    if (journey.legs.isEmpty()) return 0
    
    // Check first station (origin of first leg)
    val firstLeg = journey.legs.first()
    if (!timelineMomentHasPassed(firstLeg.actualDeparture, now)) {
        return 0 // First element is not past
    }
    
    // Check subsequent elements
    journey.legs.forEachIndexed { index, leg ->
        // Ride element
        if (!timelineMomentHasPassed(leg.actualArrival, now)) {
            return index * 2 + 1 // Ride element (after first station)
        }
        
        // Transfer or destination element
        val nextLeg = journey.legs.getOrNull(index + 1)
        if (nextLeg == null) {
            // Last station (destination)
            if (!timelineMomentHasPassed(leg.actualArrival, now)) {
                return index * 2 + 2
            }
        } else {
            // Transfer contains two stations
            if (!timelineMomentHasPassed(nextLeg.actualDeparture, now)) {
                return index * 2 + 2
            }
        }
    }
    
    return journey.legs.size * 2 // All elements are past
}

@Composable
private fun JourneyOverview(trip: TripOption, copy: Copy) {
    val firstLeg = trip.legs.firstOrNull()
    val lastLeg = trip.legs.lastOrNull()
    val cancelled = trip.primaryChangeKind() == JourneyChangeKind.CANCELLED
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.6f.gridUnitsAsDp()),
    ) {
        JourneyChangeBanner(trip, copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JourneyMetric(
                icon = LightIcons.ALARM,
                value = formatJourneyDuration(trip.durationMinutes),
                spokenLabel = "${trip.durationMinutes} ${copy.minutes}",
            )
            Spacer(Modifier.width(0.45f.gridUnitsAsDp()))
            JourneyMetric(
                icon = LightIcons.REVERSE_ORDER,
                value = trip.transfers.toString(),
                spokenLabel = "${trip.transfers} ${if (trip.transfers == 1) copy.transfer else copy.transfers}",
            )
            Spacer(Modifier.width(0.65f.gridUnitsAsDp()))
            TimeWithDelay(
                time = time(trip.plannedDeparture),
                delayMinutes = if (cancelled) 0 else firstLeg?.departureDelayMinutes ?: 0,
                variant = LightTextVariant.Detail,
            )
            SummaryArrow(Modifier.padding(horizontal = 0.18f.gridUnitsAsDp()))
            TimeWithDelay(
                time = time(trip.plannedArrival),
                delayMinutes = if (cancelled) 0 else lastLeg?.arrivalDelayMinutes ?: 0,
                variant = LightTextVariant.Detail,
            )
            Spacer(Modifier.weight(1f))
            CompactJourneyServiceSequence(trip)
        }
        LightText(
            journeyTitle(trip),
            LightTextVariant.Heading,
            modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
            maxLines = 2,
        )
    }
}

@Composable
private fun SummaryArrow(modifier: Modifier = Modifier) {
    LightText(
        "→",
        LightTextVariant.Detail,
        monospace = true,
        maxLines = 1,
        modifier = modifier.offset(y = (-6f).designVerticalPxToDp()),
    )
}

@Composable
private fun JourneyMetric(
    icon: LightIconConfiguration,
    value: String,
    spokenLabel: String,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spokenLabel
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = icon,
            size = 0.62f,
            contentDescription = null,
        )
        LightText(
            value,
            LightTextVariant.Detail,
            modifier = Modifier.padding(start = 0.2f.gridUnitsAsDp()),
            maxLines = 1,
        )
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
    ) {
        LightText(
            time,
            variant,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (delayMinutes > 0) {
            Spacer(Modifier.width(0.1f.gridUnitsAsDp()))
            InlineDelay("+$delayMinutes", variant, color, Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun InlineDelay(
    text: String,
    variant: LightTextVariant,
    color: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
) {
    val fontSize = if (variant == LightTextVariant.Subheading) 30f else 20f
    val lineHeight = if (variant == LightTextVariant.Subheading) 37.5f else 29f
    val letterSpacing = if (variant == LightTextVariant.Subheading) 0.9f else 0f
    Text(
        text = text,
        modifier = modifier,
        color = color ?: LightThemeTokens.colors.content,
        maxLines = 1,
        style = TextStyle(
            fontSize = fontSize.designVerticalPxToSp(),
            lineHeight = lineHeight.designVerticalPxToSp(),
            letterSpacing = letterSpacing.designVerticalPxToSp(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
    )
}

private fun journeyChangeLabel(trip: TripOption, copy: Copy): String? = when (trip.primaryChangeKind()) {
    JourneyChangeKind.CANCELLED -> copy.cancelled
    JourneyChangeKind.DISRUPTED -> copy.journeyChanged
    JourneyChangeKind.PLATFORM_CHANGED -> copy.platformChanged
    null -> null
}

@Composable
private fun JourneyChangeBanner(trip: TripOption, copy: Copy) {
    val label = journeyChangeLabel(trip, copy) ?: return
    OperationalStatusBanner(
        label = label,
        modifier = Modifier.padding(vertical = 0.45f.gridUnitsAsDp()),
    )
}

@Composable
private fun JourneyChangeBadge(trip: TripOption, copy: Copy, onLightBackground: Boolean) {
    val label = journeyChangeLabel(trip, copy) ?: return
    Badge(label, inverted = !onLightBackground)
}

@Composable
private fun DepartureChangeBanner(departure: Departure, copy: Copy) {
    val label = when {
        departure.cancelled -> copy.cancelled
        platformChanged(departure.plannedTrack, departure.actualTrack) -> copy.platformChanged
        else -> null
    } ?: return
    OperationalStatusBanner(
        label = label,
        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
    )
}

@Composable
private fun OperationalStatusBanner(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(LightThemeTokens.colors.content)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(horizontal = 0.55f.gridUnitsAsDp(), vertical = 0.3f.gridUnitsAsDp()),
    ) {
        LightText(
            label.uppercase(Locale.ROOT),
            LightTextVariant.Detail,
            color = LightThemeTokens.colors.background,
            monospace = true,
            maxLines = 1,
        )
    }
}

@Composable
private fun JourneyTimeRange(
    trip: TripOption,
    variant: LightTextVariant,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val cancelled = trip.primaryChangeKind() == JourneyChangeKind.CANCELLED
    val departureDelay = if (cancelled) 0 else trip.legs.firstOrNull()?.departureDelayMinutes ?: 0
    val arrivalDelay = if (cancelled) 0 else trip.legs.lastOrNull()?.arrivalDelayMinutes ?: 0
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TimeWithDelay(time(trip.plannedDeparture), departureDelay, variant, color = color)
        LightText(" – ", variant, color = color, monospace = true, maxLines = 1)
        TimeWithDelay(time(trip.plannedArrival), arrivalDelay, variant, color = color)
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
private fun SecondaryAction(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(vertical = 0.35f.gridUnitsAsDp())
            .height(1.8f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.35f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            title,
            LightTextVariant.Paragraph,
            align = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun JourneyTimeline(trip: TripOption, copy: Copy, now: Instant? = null, scrollState: ScrollState? = null) {
    if (trip.legs.isEmpty()) {
        Body(copy.noJourneys)
        return
    }
    trip.legs.forEachIndexed { index, leg ->
        if (index == 0) {
            TimelineStation(
                station = leg.origin.name,
                plannedTime = leg.plannedDeparture,
                plannedPlatform = leg.plannedDeparturePlatform,
                actualPlatform = leg.actualDeparturePlatform,
                delayMinutes = if (leg.cancelled) 0 else leg.departureDelayMinutes,
                cancelled = false,
                copy = copy,
                connectBelow = true,
                isPast = timelineMomentHasPassed(leg.actualDeparture, now),
            )
        }
        TimelineRide(leg, copy, isPast = timelineMomentHasPassed(leg.actualArrival, now))
        val nextLeg = trip.legs.getOrNull(index + 1)
        if (nextLeg == null) {
            TimelineStation(
                station = leg.destination.name,
                plannedTime = leg.plannedArrival,
                plannedPlatform = leg.plannedArrivalPlatform,
                actualPlatform = leg.actualArrivalPlatform,
                delayMinutes = if (leg.cancelled) 0 else leg.arrivalDelayMinutes,
                cancelled = false,
                copy = copy,
                connectAbove = true,
                isPast = timelineMomentHasPassed(leg.actualArrival, now),
            )
        } else {
            TimelineTransfer(
                leg,
                nextLeg,
                copy,
                isPast = timelineMomentHasPassed(nextLeg.actualDeparture, now),
            )
        }
    }
}

@Composable
private fun TimelineStation(
    station: String,
    plannedTime: String,
    plannedPlatform: String?,
    actualPlatform: String?,
    delayMinutes: Int,
    cancelled: Boolean,
    copy: Copy,
    markerInverted: Boolean = false,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
    isPast: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .timelinePastAlpha(isPast),
    ) {
        Column(
            modifier = Modifier
                .width(timelineTimeColumnGridUnits.gridUnitsAsDp())
                .offset(y = timelineTimeOpticalOffsetPx.designVerticalPxToDp())
                .padding(
                    end = timelineTimeEndPaddingGridUnits.gridUnitsAsDp(),
                    bottom = timelineStationBottomPaddingGridUnits.gridUnitsAsDp(),
                ),
            horizontalAlignment = Alignment.End,
        ) {
            LightText(
                time(plannedTime),
                LightTextVariant.Subheading,
                align = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
            if (delayMinutes > 0) {
                TimelineDelay(
                    "+$delayMinutes",
                    Modifier.padding(top = 0.05f.gridUnitsAsDp()),
                )
            }
        }
        TimelineRail(
            connectAbove = connectAbove,
            connectBelow = connectBelow,
            markerInverted = markerInverted,
        )
        Column(
            Modifier
                .weight(1f)
                .offset(y = timelineStationOpticalOffsetPx.designVerticalPxToDp())
                .padding(
                    start = timelineContentPaddingGridUnits.gridUnitsAsDp(),
                    bottom = timelineStationBottomPaddingGridUnits.gridUnitsAsDp(),
                ),
        ) {
            Row(Modifier.fillMaxWidth()) {
                LightText(
                    station,
                    LightTextVariant.Subheading,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                    maxLines = 2,
                )
                displayedPlatform(plannedPlatform, actualPlatform)?.let {
                    Box(Modifier.padding(start = 0.3f.gridUnitsAsDp()).alignByBaseline()) {
                        PlatformChangeBadge(plannedPlatform, actualPlatform)
                    }
                }
            }
            if (cancelled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 0.3f.gridUnitsAsDp()),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Badge(copy.cancelled, inverted = true)
                }
            }
        }
    }
}

@Composable
private fun TimelineRide(leg: TripLeg, copy: Copy, isPast: Boolean = false) {
    val timeColumnWidth = timelineTimeColumnGridUnits.gridUnitsAsDp()
    val railColumnWidth = timelineRailColumnGridUnits.gridUnitsAsDp()
    val lineWidth = 2f.designVerticalPxToDp()
    val lineColour = LightThemeTokens.colors.content
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val centreX = timeColumnWidth.toPx() + railColumnWidth.toPx() / 2f
                drawLine(
                    color = lineColour,
                    start = Offset(centreX, 0f),
                    end = Offset(centreX, size.height),
                    strokeWidth = lineWidth.toPx(),
                )
            }
            .timelinePastAlpha(isPast),
    ) {
        Spacer(Modifier.width(timelineTimeColumnGridUnits.gridUnitsAsDp()))
        Spacer(Modifier.width(timelineRailColumnGridUnits.gridUnitsAsDp()))
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = timelineContentPaddingGridUnits.gridUnitsAsDp(),
                    top = timelineRideTopPaddingGridUnits.gridUnitsAsDp(),
                    bottom = timelineRideBottomPaddingGridUnits.gridUnitsAsDp(),
                ),
        ) {
            TimelineServicePanel(leg, copy)
            leg.messages.forEach { message ->
                LightText(
                    message,
                    LightTextVariant.Detail,
                    modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()),
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun TimelineServicePanel(leg: TripLeg, copy: Copy) {
    val service = leg.trainType.trim()
    val destination = leg.serviceDestinationName?.takeIf(String::isNotBlank)
    if (service.isBlank() && destination == null && !leg.cancelled) return
    val cancelled = leg.cancelled
    val panelBackground = if (cancelled) {
        LightThemeTokens.colors.content
    } else {
        LightThemeTokens.colors.contentSecondary.copy(alpha = 0.28f)
    }
    val panelForeground = if (cancelled) {
        LightThemeTokens.colors.background
    } else {
        LightThemeTokens.colors.content
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(panelBackground)
            .padding(horizontal = 0.65f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
    ) {
        if (cancelled) {
            LightText(
                copy.cancelled.uppercase(Locale.ROOT),
                LightTextVariant.Superfine,
                color = panelForeground,
                monospace = true,
                maxLines = 1,
            )
        }
        if (service.isNotBlank()) {
            LightText(
                service.uppercase(Locale.ROOT),
                LightTextVariant.Superfine,
                color = panelForeground,
                monospace = true,
                modifier = if (cancelled) Modifier.padding(top = 0.2f.gridUnitsAsDp()) else Modifier,
                maxLines = 1,
            )
        }
        destination?.let {
            LightText(
                "${copy.towards} $it",
                LightTextVariant.Detail,
                color = panelForeground,
                modifier = if (service.isBlank()) Modifier else Modifier.padding(top = 0.2f.gridUnitsAsDp()),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TimelineTransfer(arrivingLeg: TripLeg, departingLeg: TripLeg, copy: Copy, isPast: Boolean = false) {
    val station = arrivingLeg.destination.name
    val arrivalPlatform = arrivingLeg.actualArrivalPlatform ?: arrivingLeg.plannedArrivalPlatform
    val departurePlatform = departingLeg.actualDeparturePlatform ?: departingLeg.plannedDeparturePlatform

    Column(Modifier.timelinePastAlpha(isPast)) {
        TimelineStation(
            station = station,
            plannedTime = arrivingLeg.plannedArrival,
            plannedPlatform = arrivingLeg.plannedArrivalPlatform,
            actualPlatform = arrivingLeg.actualArrivalPlatform,
            delayMinutes = if (arrivingLeg.cancelled) 0 else arrivingLeg.arrivalDelayMinutes,
            cancelled = false,
            copy = copy,
            markerInverted = true,
            connectAbove = true,
            connectBelow = false,
        )
        TimelineTransferWait(
            durationMinutes = transferMinutes(arrivingLeg, departingLeg),
            arrivalPlatform = arrivalPlatform,
            departurePlatform = departurePlatform,
            copy = copy,
        )
        TimelineStation(
            station = station,
            plannedTime = departingLeg.plannedDeparture,
            plannedPlatform = departingLeg.plannedDeparturePlatform,
            actualPlatform = departingLeg.actualDeparturePlatform,
            delayMinutes = if (departingLeg.cancelled) 0 else departingLeg.departureDelayMinutes,
            cancelled = false,
            copy = copy,
            connectAbove = false,
            connectBelow = true,
        )
    }
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

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = timelineTransferGapGridUnits.gridUnitsAsDp()),
    ) {
        Spacer(
            Modifier.width(
                (timelineTimeColumnGridUnits + timelineRailColumnGridUnits + timelineContentPaddingGridUnits).gridUnitsAsDp(),
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .background(LightThemeTokens.colors.content)
                .padding(horizontal = 0.7f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
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
private fun TimelineDelay(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LightThemeTokens.colors.content,
        maxLines = 1,
        modifier = modifier,
        style = TextStyle(
            fontSize = 24f.designVerticalPxToSp(),
            lineHeight = 27f.designVerticalPxToSp(),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun TimelineRail(
    connectAbove: Boolean,
    connectBelow: Boolean,
    markerInverted: Boolean,
) {
    val markerLineCentreDp = timelineStationMarkerCentrePx.designVerticalPxToDp()
    val markerSize = 0.34f.gridUnitsAsDp()
    val lineWidth = 2f.designVerticalPxToDp()
    val lineColour = LightThemeTokens.colors.content
    Box(
        Modifier
            .width(timelineRailColumnGridUnits.gridUnitsAsDp())
            .fillMaxHeight()
            .drawBehind {
                val centreX = size.width / 2f
                val centreY = markerLineCentreDp.toPx().coerceIn(0f, size.height)
                val strokeWidth = lineWidth.toPx()
                if (connectAbove) {
                    drawLine(
                        color = lineColour,
                        start = Offset(centreX, 0f),
                        end = Offset(centreX, centreY),
                        strokeWidth = strokeWidth,
                    )
                }
                if (connectBelow) {
                    drawLine(
                        color = lineColour,
                        start = Offset(centreX, centreY),
                        end = Offset(centreX, size.height),
                        strokeWidth = strokeWidth,
                    )
                }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = markerLineCentreDp - markerSize / 2)
                .width(markerSize)
                .height(markerSize)
                .background(if (markerInverted) LightThemeTokens.colors.background else LightThemeTokens.colors.content)
                .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content),
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
    ScreenFrame(copy.favourites, vm::back) {
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
    ScreenFrame(copy.settings, vm::back) {
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
        ListAction(copy.privacy) { vm.openPrivacy() }
        Section(copy.connection)
        StatusPanel(
            copy.connection,
            if (state.persisted.installSecret.isNullOrBlank()) copy.notConfigured else copy.configured,
            connected = !state.persisted.installSecret.isNullOrBlank(),
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LightText(
                copy.liveJourney.uppercase(Locale.ROOT),
                LightTextVariant.Superfine,
                color = LightThemeTokens.colors.background,
                monospace = true,
                modifier = Modifier.weight(1f),
            )
            JourneyChangeBadge(journey, copy, onLightBackground = true)
        }
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
        LiveJourneySummary(
            journey = journey,
            copy = copy,
            modifier = Modifier.fillMaxWidth().padding(top = 0.25f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun LiveJourneySummary(journey: TripOption, copy: Copy, modifier: Modifier = Modifier) {
    val cancelled = journey.primaryChangeKind() == JourneyChangeKind.CANCELLED
    val departureDelay = if (cancelled) 0 else journey.legs.firstOrNull()?.departureDelayMinutes ?: 0
    val arrivalDelay = if (cancelled) 0 else journey.legs.lastOrNull()?.arrivalDelayMinutes ?: 0
    val text = buildAnnotatedString {
        append(time(journey.plannedDeparture))
        if (departureDelay > 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("+$departureDelay") }
        }
        append(" – ")
        append(time(journey.plannedArrival))
        if (arrivalDelay > 0) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("+$arrivalDelay") }
        }
        append(" · ${tripSummary(journey, copy)}")
    }
    val detailStyle = LightThemeTokens.typography.detail
    Text(
        text = text,
        modifier = modifier,
        color = LightThemeTokens.colors.background,
        maxLines = 1,
        style = detailStyle.copy(
            fontSize = detailStyle.fontSize.value.designVerticalPxToSp(),
            lineHeight = detailStyle.lineHeight.value.designVerticalPxToSp(),
        ),
    )
}

@Composable
private fun HomeShortcut(
    icon: LightIconConfiguration,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
            HomeMenuIcon(icon)
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
        PlannerField("B", copy.destination, state.plannerDestination?.name ?: copy.chooseStation, divider = false) {
            vm.openStationSearch(StationPurpose.DESTINATION)
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
private fun PlannerAction(
    title: String,
    prominent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (prominent) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (prominent) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Box(
        modifier
            .height(1.8f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            title,
            LightTextVariant.Paragraph,
            color = foreground,
            align = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun JourneyResultCard(trip: TripOption, copy: Copy, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
    ) {
        JourneyTimeRange(
            trip = trip,
            variant = LightTextVariant.Subheading,
        )
        JourneyChangeBanner(trip, copy)
        LightText(
            tripSummary(trip, copy),
            LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 0.35f.gridUnitsAsDp()),
            maxLines = 1,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 0.55f.gridUnitsAsDp())
                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.28f))
                .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.4f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                JourneyServiceSequence(trip)
            }
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
    }
}

@Composable
private fun JourneyServiceSequence(trip: TripOption, modifier: Modifier = Modifier) {
    val services = journeyServiceLabels(trip)
    if (services.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        services.forEachIndexed { index, service ->
            if (index > 0) {
                LightText(
                    "→",
                    LightTextVariant.Detail,
                    monospace = true,
                    modifier = Modifier.padding(horizontal = 0.2f.gridUnitsAsDp()),
                )
            }
            TrainServiceBadge(service)
        }
    }
}

@Composable
private fun CompactJourneyServiceSequence(trip: TripOption, modifier: Modifier = Modifier) {
    val services = journeyServiceLabels(trip)
    if (services.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        services.forEachIndexed { index, service ->
            if (index > 0) {
                LightText(
                    "→",
                    LightTextVariant.Superfine,
                    monospace = true,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 0.12f.gridUnitsAsDp())
                        .offset(y = (-2f).designVerticalPxToDp()),
                )
            }
            CompactServiceBadge(service)
        }
    }
}

@Composable
private fun CompactServiceBadge(service: String) {
    Box(
        modifier = Modifier
            .height(0.9f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.28f))
            .padding(horizontal = 0.3f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            service.uppercase(Locale.ROOT),
            LightTextVariant.Superfine,
            monospace = true,
            maxLines = 1,
        )
    }
}

@Composable
private fun DepartureHero(departure: Departure, copy: Copy) {
    val service = departure.trainType.trim()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.6f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            DepartureTimeHeadline(departure = departure)
            Spacer(Modifier.width(0.75f.gridUnitsAsDp()))
            DepartureHeadline(
                text = departure.direction,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 0.45f.gridUnitsAsDp()),
                maxLines = 2,
            )
            if (service.isNotBlank()) TrainServiceBadge(service)
            displayedPlatform(departure.plannedTrack, departure.actualTrack)?.let {
                if (service.isNotBlank()) Spacer(Modifier.width(0.35f.gridUnitsAsDp()))
                PlatformChangeBadge(departure.plannedTrack, departure.actualTrack)
            }
        }
        DepartureChangeBanner(departure, copy)
    }
}

@Composable
private fun DepartureStopRow(stop: DepartureStop, first: Boolean, last: Boolean, copy: Copy) {
    TimelineStation(
        station = stop.name,
        plannedTime = departureStopPlannedTime(stop),
        plannedPlatform = stop.plannedPlatform,
        actualPlatform = stop.actualPlatform,
        delayMinutes = departureStopDelayMinutes(stop),
        cancelled = false,
        copy = copy,
        markerInverted = !first && !last,
        connectAbove = !first,
        connectBelow = !last,
    )
}

@Composable
private fun DepartureRouteStationRow(station: String, first: Boolean, last: Boolean, copy: Copy) {
    TimelineStation(
        station = station,
        plannedTime = "",
        plannedPlatform = null,
        actualPlatform = null,
        delayMinutes = 0,
        cancelled = false,
        copy = copy,
        markerInverted = !first && !last,
        connectAbove = !first,
        connectBelow = !last,
    )
}

@Composable
private fun DisruptionCard(disruption: Disruption, copy: Copy, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(0.7f.gridUnitsAsDp()),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(
                if (disruption.isMaintenance) copy.planned else copy.disruption,
                inverted = !disruption.isMaintenance,
            )
            LightText("→", LightTextVariant.Paragraph, monospace = true)
        }
        LightText(disruption.title, LightTextVariant.ParagraphWide, modifier = Modifier.padding(top = 0.4f.gridUnitsAsDp()), maxLines = 3)
        disruption.period?.takeIf(String::isNotBlank)?.let {
            LightText(it, LightTextVariant.Detail, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 2)
        }
        disruption.trajectories.firstOrNull()?.let {
            LightText(it, LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 2)
        }
    }
}

@Composable
private fun DisruptionHero(disruption: Disruption, copy: Copy) {
    val inverted = !disruption.isMaintenance
    val background = if (inverted) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (inverted) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(0.8f.gridUnitsAsDp()),
    ) {
        Badge(
            if (disruption.isMaintenance) copy.planned else copy.disruption,
            inverted = disruption.isMaintenance,
        )
        LightText(
            disruption.title,
            LightTextVariant.Subheading,
            color = foreground,
            modifier = Modifier.padding(top = 0.45f.gridUnitsAsDp()),
            maxLines = 4,
        )
        disruption.period?.takeIf(String::isNotBlank)?.let {
            LightText(it, LightTextVariant.Detail, color = foreground, modifier = Modifier.padding(top = 0.3f.gridUnitsAsDp()), maxLines = 2)
        }
    }
}

internal fun disruptionCategory(disruption: Disruption, copy: Copy): String =
    if (disruption.isMaintenance) copy.plannedMaintenance else copy.disruption

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
            FavouriteRouteAction("", copy.useRoute, prominent = true, modifier = Modifier.weight(1f)) { vm.useRoute(route) }
            FavouriteRouteAction("×", copy.removeFavourite, prominent = false, modifier = Modifier.weight(1f)) { vm.removeRoute(route) }
        }
    }
}

@Composable
private fun FavouriteRouteAction(
    marker: String,
    title: String,
    prominent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (prominent) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (prominent) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Row(
        modifier
            .height(2.15f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.45f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marker.isNotBlank()) {
            LightText(marker, LightTextVariant.Paragraph, color = foreground, monospace = true)
        }
        LightText(
            title,
            LightTextVariant.Paragraph,
            color = foreground,
            align = TextAlign.Center,
            modifier = if (marker.isBlank()) Modifier else Modifier.padding(start = 0.35f.gridUnitsAsDp()),
            maxLines = 2,
        )
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
    icon: LightIconConfiguration,
    title: String,
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
        HomeMenuIcon(icon, inverted = prominent)
        Column(Modifier.weight(1f).padding(horizontal = 0.7f.gridUnitsAsDp())) {
            LightText(title, LightTextVariant.Subheading, color = foreground, maxLines = 1)
        }
        LightText("→", LightTextVariant.Subheading, color = foreground, monospace = true)
    }
}

@Composable
private fun HomeMenuIcon(icon: LightIconConfiguration, inverted: Boolean = false) {
    if (inverted) {
        Icon(
            painter = painterResource(icon.drawableResource),
            contentDescription = null,
            tint = LightThemeTokens.colors.background,
            modifier = Modifier
                .width(0.9f.gridUnitsAsDp())
                .height(0.9f.gridUnitsAsDp()),
        )
    } else {
        LightIcon(icon, size = 0.9f, contentDescription = null)
    }
}

@Composable
private fun UtilityAction(
    title: String,
    modifier: Modifier = Modifier,
    marker: String? = null,
    icon: LightIconConfiguration? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(vertical = 0.3f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.6f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null || marker != null) {
            val opticalOffset = if (marker != null) -0.2f else -0.07f
            Box(
                modifier = Modifier
                    .width(0.9f.gridUnitsAsDp())
                    .height(0.9f.gridUnitsAsDp())
                    .offset(y = opticalOffset.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                icon?.let { HomeMenuIcon(it) }
                marker?.let { LightText(it, LightTextVariant.Subheading, monospace = true) }
            }
        }
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
            .height(1.55f.gridUnitsAsDp())
            .background(LightThemeTokens.colors.content)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            letter,
            LightTextVariant.Subheading,
            color = LightThemeTokens.colors.background,
            monospace = true,
        )
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
            .padding(vertical = 0.25f.gridUnitsAsDp())
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .lightClickable(onClick = onClick)
            .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.55f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            DepartureTimeHeadline(
                departure = departure,
                modifier = Modifier.alignByBaseline(),
            )
            DepartureHeadline(
                text = departure.direction,
                align = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.6f.gridUnitsAsDp())
                    .alignByBaseline(),
                maxLines = 2,
            )
        }
        DepartureChangeBanner(departure, copy)
        TrainPlatformBadges(
            trainType = departure.trainType,
            plannedPlatform = departure.plannedTrack,
            actualPlatform = departure.actualTrack,
        )
    }
}

@Composable
private fun DepartureTimeHeadline(departure: Departure, modifier: Modifier = Modifier) {
    val value = buildAnnotatedString {
        append(time(departure.plannedDateTime))
        if (departure.delayMinutes > 0 && !departure.cancelled) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("+${departure.delayMinutes}")
            }
        }
    }
    Text(
        text = value,
        modifier = modifier,
        color = LightThemeTokens.colors.content,
        maxLines = 1,
        style = LightThemeTokens.typography.heading.copy(
            fontSize = 34f.designVerticalPxToSp(),
            lineHeight = 42.5f.designVerticalPxToSp(),
        ),
    )
}

@Composable
private fun DepartureHeadline(
    text: String,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        color = LightThemeTokens.colors.content,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = LightThemeTokens.typography.heading.copy(
            fontSize = 34f.designVerticalPxToSp(),
            lineHeight = 42.5f.designVerticalPxToSp(),
            textAlign = align,
        ),
    )
}

@Composable
private fun TrainPlatformBadges(trainType: String, plannedPlatform: String?, actualPlatform: String?) {
    Row(
        modifier = Modifier.padding(top = 0.45f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.spacedBy(0.35f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        trainType.trim().takeIf(String::isNotBlank)?.let { TrainServiceBadge(it) }
        displayedPlatform(plannedPlatform, actualPlatform)?.let { displayed ->
            if (platformChanged(plannedPlatform, actualPlatform)) {
                LightText(
                    plannedPlatform.orEmpty().trim(),
                    LightTextVariant.Detail,
                    lighten = true,
                    monospace = true,
                    maxLines = 1,
                )
                LightText(
                    "→",
                    LightTextVariant.Detail,
                    monospace = true,
                    maxLines = 1,
                )
            }
            PlatformBadge(displayed)
        }
    }
}

@Composable
private fun TrainServiceBadge(service: String) {
    TransportBadge(service.uppercase(Locale.ROOT), inverted = false)
}

@Composable
private fun PlatformBadge(platform: String) {
    TransportBadge(platform.trim(), inverted = true)
}

@Composable
private fun PlatformChangeBadge(planned: String?, actual: String?) {
    val displayed = displayedPlatform(planned, actual) ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (platformChanged(planned, actual)) {
            LightText(
                planned.orEmpty().trim(),
                LightTextVariant.Detail,
                lighten = true,
                monospace = true,
                maxLines = 1,
            )
            LightText(
                "→",
                LightTextVariant.Detail,
                monospace = true,
                modifier = Modifier.padding(horizontal = 0.2f.gridUnitsAsDp()),
                maxLines = 1,
            )
        }
        PlatformBadge(displayed)
    }
}

@Composable
private fun TransportBadge(text: String, inverted: Boolean) {
    val background = if (inverted) LightThemeTokens.colors.content else LightThemeTokens.colors.background
    val foreground = if (inverted) LightThemeTokens.colors.background else LightThemeTokens.colors.content
    Box(
        Modifier
            .height(1.3f.gridUnitsAsDp())
            .background(background)
            .border(2f.designVerticalPxToDp(), LightThemeTokens.colors.content)
            .padding(horizontal = 0.5f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Paragraph,
            color = foreground,
            monospace = true,
            maxLines = 1,
        )
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

private const val timelineTimeColumnGridUnits = 3.75f
private const val timelineRailColumnGridUnits = 0.75f
private const val timelineContentPaddingGridUnits = 0.55f
private const val timelineTimeEndPaddingGridUnits = 0.4f
private const val timelineStationTopPaddingGridUnits = 0.45f
private const val timelineStationBottomPaddingGridUnits = 0.55f
private const val timelineRideTopPaddingGridUnits = 0.45f
private const val timelineRideBottomPaddingGridUnits = 0.8f
private const val timelineStationMarkerCentrePx = 18.75f
// Digits and mixed-case station names sit at different optical heights despite sharing a baseline.
private const val timelineTimeOpticalOffsetPx = 6f
private const val timelineStationOpticalOffsetPx = 3f
private const val timelineTransferGapGridUnits = 0.9f
private const val timelinePastContentAlpha = 0.46f
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val compactOffsetFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX")
private val amsterdam = ZoneId.of("Europe/Amsterdam")
private fun parsedInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value, compactOffsetFormatter).toInstant() }.getOrNull()
internal fun timelineMomentHasPassed(value: String, now: Instant?): Boolean {
    val referenceNow = now ?: return false
    val moment = parsedInstant(value) ?: return false
    return !referenceNow.isBefore(moment)
}
private fun Modifier.timelinePastAlpha(isPast: Boolean): Modifier =
    if (isPast) alpha(timelinePastContentAlpha) else this
private fun transferMinutes(arrivingLeg: TripLeg, departingLeg: TripLeg): Int? {
    arrivingLeg.transferMinutesAfterLeg?.let { return it }
    val arrival = parsedInstant(arrivingLeg.actualArrival) ?: return null
    val departure = parsedInstant(departingLeg.actualDeparture) ?: return null
    return Duration.between(arrival, departure).toMinutes().toInt().takeIf { it >= 0 }
}
internal fun time(value: String): String = parsedInstant(value)?.atZone(amsterdam)?.format(timeFormatter) ?: value
internal fun departureStopPlannedTime(stop: DepartureStop): String =
    stop.plannedDeparture ?: stop.plannedArrival ?: stop.actualDeparture ?: stop.actualArrival.orEmpty()
internal fun departureStopDelayMinutes(stop: DepartureStop): Int {
    val planned = stop.plannedDeparture ?: stop.plannedArrival ?: return 0
    val actual = if (stop.plannedDeparture != null) stop.actualDeparture else stop.actualArrival
    val plannedInstant = parsedInstant(planned) ?: return 0
    val actualInstant = actual?.let(::parsedInstant) ?: return 0
    return Duration.between(plannedInstant, actualInstant).toMinutes().toInt().coerceAtLeast(0)
}
private fun price(value: JourneyPrice): String = String.format(Locale.UK, "€ %.2f", value.amountEuroCents / 100.0)
private fun journeyTitle(journey: TripOption): String = "${journey.legs.firstOrNull()?.origin?.name.orEmpty()} → ${journey.legs.lastOrNull()?.destination?.name.orEmpty()}"
internal fun journeyServiceLabels(trip: TripOption): List<String> =
    trip.legs.mapNotNull { leg -> leg.trainType.trim().takeIf(String::isNotBlank) }
private fun tripSummary(trip: TripOption, copy: Copy): String {
    val transferText = "${trip.transfers} ${if (trip.transfers == 1) copy.transfer else copy.transfers}"
    return "${formatJourneyDuration(trip.durationMinutes)} · $transferText"
}
internal fun formatJourneyDuration(durationMinutes: Int): String =
    "${durationMinutes.coerceAtLeast(0) / 60}:${(durationMinutes.coerceAtLeast(0) % 60).toString().padStart(2, '0')}"
internal fun departureDisplayTime(departure: Departure): String = buildString {
    append(time(departure.plannedDateTime))
    if (departure.delayMinutes > 0 && !departure.cancelled) append("+${departure.delayMinutes}")
}
