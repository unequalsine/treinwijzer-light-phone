package nl.treinwijzer.lightphone

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

enum class StationPurpose { INFO, DISRUPTIONS, ORIGIN, DESTINATION, VIA }

sealed interface ScreenMode {
    data object Loading : ScreenMode
    data object Home : ScreenMode
    data class StationIndex(val purpose: StationPurpose) : ScreenMode
    data class StationResults(val purpose: StationPurpose, val query: String, val stations: List<Station>) : ScreenMode
    data class Departures(val station: Station, val departures: List<Departure>) : ScreenMode
    data class DepartureDetails(val station: Station, val departure: Departure) : ScreenMode
    data class Disruptions(val station: Station, val disruptions: List<Disruption>) : ScreenMode
    data class DisruptionDetails(val disruption: Disruption) : ScreenMode
    data object Planner : ScreenMode
    data class DateTimeInput(val session: Int) : ScreenMode
    data class Trips(val request: PlannerRequest, val trips: List<TripOption>, val recovery: Boolean = false) : ScreenMode
    data class TripDetails(val trip: TripOption, val request: PlannerRequest?) : ScreenMode
    data object Favourites : ScreenMode
    data object Settings : ScreenMode
    data object NearestUnavailable : ScreenMode
    data class Active(val journey: TripOption) : ScreenMode
}

data class TreinwijzerUiState(
    val mode: ScreenMode = ScreenMode.Loading,
    val persisted: PersistedState = PersistedState(),
    val plannerOrigin: Station? = null,
    val plannerDestination: Station? = null,
    val plannerVia: Station? = null,
    val busy: Boolean = false,
    val errorModal: String? = null,
    val alertModal: LightAlert? = null,
    val statusMessage: String? = null,
)

class TreinwijzerViewModel(dataStore: DataStore<Preferences>) : LightViewModel<Unit>() {
    private val preferences = TreinwijzerPreferences(dataStore)
    private val api = TreinwijzerApi()
    private val _uiState = MutableStateFlow(TreinwijzerUiState())
    val uiState: StateFlow<TreinwijzerUiState> = _uiState.asStateFlow()
    private var foregroundRefresh: Job? = null
    private var dateTimeInputSession = 0
    private val handledUpdates = LinkedHashSet<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) { initialise() }
        viewModelScope.launch(Dispatchers.IO) {
            PushBridge.endpoint.collectLatest { endpoint ->
                if (!endpoint.isNullOrBlank()) registerPushIfNeeded(endpoint)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            PushBridge.signals.collect { envelope ->
                if (handledUpdates.add(envelope.updateId)) {
                    while (handledUpdates.size > 20) handledUpdates.remove(handledUpdates.first())
                    envelope.alert?.let { showAndAcknowledge(it) }
                    refreshActiveJourney(showBusy = false)
                }
            }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        startForegroundRefresh()
        viewModelScope.launch(Dispatchers.IO) {
            refreshActiveJourney(showBusy = false)
            fetchEventInbox()
        }
    }

    override fun onAppPause() {
        foregroundRefresh?.cancel()
        foregroundRefresh = null
    }

    override fun onBackPressed(): Boolean {
        val mode = _uiState.value.mode
        if (mode == ScreenMode.Home || mode == ScreenMode.Loading) return false
        _uiState.update { it.copy(mode = when (mode) {
            is ScreenMode.StationResults -> ScreenMode.StationIndex(mode.purpose)
            is ScreenMode.StationIndex -> when (mode.purpose) {
                StationPurpose.ORIGIN, StationPurpose.DESTINATION, StationPurpose.VIA -> ScreenMode.Planner
                StationPurpose.INFO, StationPurpose.DISRUPTIONS -> ScreenMode.Home
            }
            is ScreenMode.DepartureDetails -> ScreenMode.Departures(mode.station, listOf(mode.departure))
            is ScreenMode.DisruptionDetails -> ScreenMode.Home
            is ScreenMode.TripDetails -> mode.request?.let { request ->
                ScreenMode.Trips(request, listOf(mode.trip))
            } ?: ScreenMode.Home
            is ScreenMode.Active -> ScreenMode.Home
            else -> ScreenMode.Home
        }, errorModal = null) }
        return true
    }

    private suspend fun initialise() {
        val stored = preferences.load()
        val registered = ensureIdentity(stored)
        val state = registered.copy(
            stations = if (registered.stations.isEmpty()) runCatching { api.stations(registered.identity()) }.getOrDefault(emptyList()) else registered.stations,
        )
        preferences.save(state)
        withContext(Dispatchers.Main) {
            _uiState.value = TreinwijzerUiState(
                mode = ScreenMode.Home,
                persisted = state,
                plannerOrigin = state.recentStations.firstOrNull(),
            )
        }
        PushBridge.endpoint.value?.let { registerPushIfNeeded(it) }
        fetchEventInbox()
        if (state.activeJourney != null) startForegroundRefresh()
    }

    private suspend fun ensureIdentity(state: PersistedState): PersistedState {
        if (!state.installSecret.isNullOrBlank()) return state
        return try {
            val response = api.registerInstall(state.installId)
            state.copy(installId = response.installId, installSecret = response.installSecret)
        } catch (error: Throwable) {
            showError(error)
            state
        }
    }

    private fun PersistedState.identity(): InstallIdentity {
        val secret = installSecret ?: throw ApiException("Install registration is incomplete")
        return InstallIdentity(installId, secret)
    }

    fun openStationSearch(purpose: StationPurpose) {
        _uiState.update { it.copy(mode = ScreenMode.StationIndex(purpose), errorModal = null) }
    }

    fun selectStationLetter(letter: String, purpose: StationPurpose) {
        val stations = filterStationsByLetter(_uiState.value.persisted.stations, letter)
        updateMode(ScreenMode.StationResults(purpose, letter.uppercase(), stations))
    }

    fun backFromStationPicker(purpose: StationPurpose) {
        updateMode(
            when (purpose) {
                StationPurpose.ORIGIN, StationPurpose.DESTINATION, StationPurpose.VIA -> ScreenMode.Planner
                StationPurpose.INFO, StationPurpose.DISRUPTIONS -> ScreenMode.Home
            },
        )
    }

    fun selectStation(station: Station, purpose: StationPurpose) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state ->
                state.copy(recentStations = (listOf(station) + state.recentStations.filterNot { it.code == station.code }).take(8))
            }
        }
        when (purpose) {
            StationPurpose.INFO -> loadDepartures(station)
            StationPurpose.DISRUPTIONS -> loadDisruptions(station)
            StationPurpose.ORIGIN -> {
                _uiState.update { it.copy(plannerOrigin = station, mode = ScreenMode.Planner) }
            }
            StationPurpose.DESTINATION -> {
                _uiState.update { it.copy(plannerDestination = station, mode = ScreenMode.Planner) }
            }
            StationPurpose.VIA -> {
                _uiState.update { it.copy(plannerVia = station, mode = ScreenMode.Planner) }
            }
        }
    }

    fun loadDepartures(station: Station) = launchBusy {
        val state = _uiState.value.persisted
        updateMode(ScreenMode.Departures(station, api.departures(state.identity(), station, state.language)))
    }

    fun loadDepartureDetails(station: Station, departure: Departure) = launchBusy {
        val state = _uiState.value.persisted
        updateMode(ScreenMode.DepartureDetails(station, api.departureDetail(state.identity(), station, departure, state.language)))
    }

    fun loadDisruptions(station: Station) = launchBusy {
        val state = _uiState.value.persisted
        updateMode(ScreenMode.Disruptions(station, api.disruptions(state.identity(), station, state.language)))
    }

    fun showDisruption(disruption: Disruption) = updateMode(ScreenMode.DisruptionDetails(disruption))

    fun openPlanner() = updateMode(ScreenMode.Planner)

    fun swapPlannerStations() {
        _uiState.update { it.copy(plannerOrigin = it.plannerDestination, plannerDestination = it.plannerOrigin) }
    }

    fun clearVia() = _uiState.update { it.copy(plannerVia = null) }

    fun togglePlannerTimeMode() {
        val state = _uiState.value.persisted
        val updated = state.plannerPreferences.copy(
            timeMode = if (state.plannerPreferences.timeMode == PlannerTimeMode.DEPARTURE) PlannerTimeMode.ARRIVAL else PlannerTimeMode.DEPARTURE,
        )
        viewModelScope.launch(Dispatchers.IO) { updatePersisted { it.copy(plannerPreferences = updated) } }
    }

    fun openDateTimeInput() {
        dateTimeInputSession += 1
        updateMode(ScreenMode.DateTimeInput(dateTimeInputSession))
    }

    fun setPlannerDateTime(value: String) {
        val formatted = try {
            LocalDateTime.parse(value.trim(), DATE_INPUT_FORMAT)
                .atZone(ZoneId.of("Europe/Amsterdam"))
                .toInstant()
                .toString()
        } catch (_: DateTimeParseException) {
            _uiState.update { it.copy(errorModal = Copy(it.persisted.language).invalidDate) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state -> state.copy(plannerPreferences = state.plannerPreferences.copy(dateTime = formatted)) }
            updateMode(ScreenMode.Planner)
        }
    }

    fun useCurrentTime() {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state -> state.copy(plannerPreferences = state.plannerPreferences.copy(dateTime = null)) }
        }
    }

    fun planJourney() {
        val state = _uiState.value
        val origin = state.plannerOrigin ?: return
        val destination = state.plannerDestination ?: return
        if (origin.code.equals(destination.code, ignoreCase = true)) return
        val request = PlannerRequest(
            origin = origin,
            destination = destination,
            via = state.plannerVia,
            timeMode = state.persisted.plannerPreferences.timeMode,
            dateTime = state.persisted.plannerPreferences.dateTime,
        )
        launchBusy {
            val persisted = _uiState.value.persisted
            val trips = api.trips(persisted.identity(), request, persisted.language)
            updateMode(ScreenMode.Trips(request, trips))
        }
    }

    fun showTrip(trip: TripOption, request: PlannerRequest?) = updateMode(ScreenMode.TripDetails(trip, request))

    fun loadRecovery(trip: TripOption) {
        val transferLeg = trip.legs.firstOrNull() ?: return
        if (trip.legs.size < 2) return
        launchBusy {
            val state = _uiState.value.persisted
            val options = api.recoveryTrips(
                state.identity(),
                transferLeg.destination,
                trip.legs.last().destination,
                transferLeg.actualArrival,
                state.language,
            )
            val request = PlannerRequest(
                stationFor(transferLeg.destination, state.stations),
                stationFor(trip.legs.last().destination, state.stations),
                dateTime = transferLeg.actualArrival,
            )
            updateMode(ScreenMode.Trips(request, options, recovery = true))
        }
    }

    fun toggleFavouriteStation(station: Station) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state ->
                val exists = state.favouriteStations.any { it.code == station.code }
                state.copy(favouriteStations = if (exists) state.favouriteStations.filterNot { it.code == station.code } else state.favouriteStations + station)
            }
        }
    }

    fun saveRoute(request: PlannerRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state ->
                val route = FavouriteRoute(
                    id = UUID.randomUUID().toString(),
                    name = "${request.origin.name} – ${request.destination.name}",
                    origin = request.origin,
                    destination = request.destination,
                    via = request.via,
                )
                val duplicate = state.favouriteRoutes.any {
                    it.origin.code == route.origin.code && it.destination.code == route.destination.code && it.via?.code == route.via?.code
                }
                if (duplicate) state else state.copy(favouriteRoutes = state.favouriteRoutes + route)
            }
        }
    }

    fun removeRoute(route: FavouriteRoute) {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { it.copy(favouriteRoutes = it.favouriteRoutes.filterNot { existing -> existing.id == route.id }) }
        }
    }

    fun useRoute(route: FavouriteRoute) {
        _uiState.update {
            it.copy(
                plannerOrigin = route.origin,
                plannerDestination = route.destination,
                plannerVia = route.via,
                mode = ScreenMode.Planner,
            )
        }
    }

    fun openFavourites() = updateMode(ScreenMode.Favourites)
    fun openSettings() = updateMode(ScreenMode.Settings)
    fun openNearest() = updateMode(ScreenMode.NearestUnavailable)
    fun openActive() = _uiState.value.persisted.activeJourney?.let { updateMode(ScreenMode.Active(it)) }
    fun home() = updateMode(ScreenMode.Home)

    fun setLanguage(language: Language) {
        viewModelScope.launch(Dispatchers.IO) { updatePersisted { it.copy(language = language) } }
    }

    fun toggleAlerts() {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state ->
                state.copy(notificationPreferences = state.notificationPreferences.copy(alertsEnabled = !state.notificationPreferences.alertsEnabled))
            }
            refreshActiveRegistration()
        }
    }

    fun toggleGuidance() {
        viewModelScope.launch(Dispatchers.IO) {
            updatePersisted { state ->
                state.copy(notificationPreferences = state.notificationPreferences.copy(guidanceEnabled = !state.notificationPreferences.guidanceEnabled))
            }
            refreshActiveRegistration()
        }
    }

    fun startTracking(trip: TripOption) = launchBusy {
        val state = _uiState.value.persisted
        api.registerActiveJourney(state.identity(), trip, state.language, state.notificationPreferences)
        updatePersisted { it.copy(activeJourney = trip) }
        updateMode(ScreenMode.Active(trip))
        startForegroundRefresh()
    }

    fun stopTracking() = launchBusy {
        val state = _uiState.value.persisted
        api.clearActiveJourney(state.identity())
        updatePersisted { it.copy(activeJourney = null) }
        updateMode(ScreenMode.Home)
        foregroundRefresh?.cancel()
        foregroundRefresh = null
    }

    fun manualRefreshActive() = viewModelScope.launch(Dispatchers.IO) { refreshActiveJourney(showBusy = true) }

    private fun startForegroundRefresh() {
        if (foregroundRefresh?.isActive == true || _uiState.value.persisted.activeJourney == null) return
        foregroundRefresh = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000)
                refreshActiveJourney(showBusy = false)
            }
        }
    }

    private suspend fun refreshActiveJourney(showBusy: Boolean) {
        val state = _uiState.value.persisted
        val current = state.activeJourney ?: return
        if (showBusy) setBusy(true)
        try {
            val authoritative = api.activeJourney(state.identity()) ?: runCatching {
                val request = plannerRequestFor(current, state.stations)
                matchJourney(current, api.trips(state.identity(), request, state.language))
            }.getOrNull()
            if (authoritative != null) {
                updatePersisted { it.copy(activeJourney = authoritative) }
                if (_uiState.value.mode is ScreenMode.Active) updateMode(ScreenMode.Active(authoritative))
            }
        } catch (error: Throwable) {
            if (error !is CancellationException && showBusy) showError(error)
        } finally {
            if (showBusy) setBusy(false)
        }
    }

    private suspend fun refreshActiveRegistration() {
        val state = _uiState.value.persisted
        state.activeJourney?.let {
            runCatching { api.registerActiveJourney(state.identity(), it, state.language, state.notificationPreferences) }
        }
    }

    private suspend fun registerPushIfNeeded(endpoint: String) {
        val state = _uiState.value.persisted
        if (state.installSecret.isNullOrBlank() || state.registeredPushEndpoint == endpoint) return
        runCatching { api.registerPush(state.identity(), endpoint) }
            .onSuccess { updatePersisted { it.copy(registeredPushEndpoint = endpoint) } }
    }

    private suspend fun fetchEventInbox() {
        val state = _uiState.value.persisted
        if (state.installSecret.isNullOrBlank()) return
        runCatching { api.events(state.identity()) }
            .getOrDefault(emptyList())
            .firstOrNull()
            ?.let { showAndAcknowledge(it) }
    }

    private suspend fun showAndAcknowledge(alert: LightAlert) {
        withContext(Dispatchers.Main) { _uiState.update { it.copy(alertModal = alert) } }
        val state = _uiState.value.persisted
        runCatching { api.acknowledgeEvents(state.identity(), listOf(alert.id)) }
    }

    fun dismissAlert() {
        _uiState.update { it.copy(alertModal = null) }
        viewModelScope.launch(Dispatchers.IO) { fetchEventInbox() }
    }

    fun dismissError() = _uiState.update { it.copy(errorModal = null) }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            setBusy(true)
            try {
                block()
            } catch (error: Throwable) {
                if (error !is CancellationException) showError(error)
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun updatePersisted(transform: (PersistedState) -> PersistedState) {
        val updated = transform(_uiState.value.persisted)
        preferences.save(updated)
        withContext(Dispatchers.Main) { _uiState.update { it.copy(persisted = updated) } }
    }

    private suspend fun setBusy(busy: Boolean) = withContext(Dispatchers.Main) { _uiState.update { it.copy(busy = busy) } }
    private fun updateMode(mode: ScreenMode) = _uiState.update { it.copy(mode = mode, errorModal = null) }

    private suspend fun showError(error: Throwable) = withContext(Dispatchers.Main) {
        _uiState.update { state ->
            state.copy(errorModal = if (BuildConfig.WORKER_ACCESS_TOKEN.isBlank()) Copy(state.persisted.language).notConfigured else Copy(state.persisted.language).networkError)
        }
    }

    companion object {
        private val DATE_INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val STATION_INDEX_PREFIXES = setOf("s", "t", "de", "den", "het")

        internal fun filterStations(stations: List<Station>, query: String): List<Station> {
            val terms = normalise(query).split(' ').filter(String::isNotBlank)
            return stations.asSequence()
                .map { station ->
                    val searchable = normalise(listOf(station.code, station.name).plus(station.synonyms).joinToString(" "))
                    station to terms.all(searchable::contains)
                }
                .filter { it.second }
                .map { it.first }
                .sortedWith(compareBy<Station> { !normalise(it.name).startsWith(normalise(query)) }.thenBy { it.name })
                .take(30)
                .toList()
        }

        internal fun filterStationsByLetter(stations: List<Station>, letter: String): List<Station> {
            val selected = letter.trim().uppercase().firstOrNull()?.toString() ?: return emptyList()
            return stations
                .filter { selected in stationIndexLetters(it) }
                .sortedBy { normalise(it.name) }
        }

        private fun stationIndexLetters(station: Station): Set<String> = buildSet {
            (listOf(station.name) + station.synonyms).forEach { name ->
                val tokens = normalise(name).split(' ').filter(String::isNotBlank)
                val first = tokens.firstOrNull() ?: return@forEach
                add(first.first().uppercase())
                if (first in STATION_INDEX_PREFIXES && tokens.size > 1) {
                    add(tokens[1].first().uppercase())
                }
            }
        }

        private fun normalise(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

        internal fun matchJourney(previous: TripOption, candidates: List<TripOption>): TripOption? {
            candidates.firstOrNull { it.id == previous.id }?.let { return it }
            previous.routeId?.let { routeId -> candidates.firstOrNull { it.routeId == routeId }?.let { return it } }
            val origin = previous.legs.firstOrNull()?.origin?.code
            val destination = previous.legs.lastOrNull()?.destination?.code
            return candidates
                .filter { it.legs.firstOrNull()?.origin?.code == origin && it.legs.lastOrNull()?.destination?.code == destination }
                .minByOrNull { kotlin.math.abs(java.time.Instant.parse(it.plannedDeparture).epochSecond - java.time.Instant.parse(previous.plannedDeparture).epochSecond) }
        }

        private fun stationFor(stop: StationStop, stations: List<Station>): Station = stations.firstOrNull { it.code == stop.code }
            ?: Station(stop.code, stop.name, "NL")

        private fun plannerRequestFor(journey: TripOption, stations: List<Station>): PlannerRequest {
            val first = journey.legs.first()
            val last = journey.legs.last()
            return PlannerRequest(
                stationFor(first.origin, stations),
                stationFor(last.destination, stations),
                dateTime = journey.plannedDeparture,
            )
        }
    }
}
