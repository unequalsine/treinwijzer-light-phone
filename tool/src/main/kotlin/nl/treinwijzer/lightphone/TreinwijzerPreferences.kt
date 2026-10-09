package nl.treinwijzer.lightphone

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class InstallIdentity(val installId: String, val installSecret: String)

@Serializable
data class PersistedState(
    val installId: String = UUID.randomUUID().toString(),
    val installSecret: String? = null,
    val stations: List<Station> = emptyList(),
    val favouriteStations: List<Station> = emptyList(),
    val favouriteRoutes: List<FavouriteRoute> = emptyList(),
    val recentStations: List<Station> = emptyList(),
    val plannerPreferences: PlannerPreferences = PlannerPreferences(),
    val notificationPreferences: JourneyNotificationPreferences = JourneyNotificationPreferences(),
    val activeJourney: TripOption? = null,
    val language: Language = Language.DUTCH,
    val registeredPushEndpoint: String? = null,
    val serviceBaseUrl: String? = null,
    val privacyAccepted: Boolean = false,
)

internal fun PersistedState.forBackend(baseUrl: String, newInstallId: () -> String = { UUID.randomUUID().toString() }): PersistedState {
    val normalizedUrl = baseUrl.trimEnd('/')
    val previousUrl = serviceBaseUrl?.trimEnd('/') ?: "https://treinwijzer-light-dev.unequalsine.workers.dev"
    return if (previousUrl == normalizedUrl) copy(serviceBaseUrl = normalizedUrl) else copy(
        installId = newInstallId(),
        installSecret = null,
        registeredPushEndpoint = null,
        activeJourney = null,
        serviceBaseUrl = normalizedUrl,
    )
}

class TreinwijzerPreferences(private val dataStore: DataStore<Preferences>) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun load(): PersistedState {
        val raw = dataStore.data.first()[STATE]
        if (raw == null) {
            val initial = PersistedState()
            save(initial)
            return initial
        }
        return runCatching { json.decodeFromString<PersistedState>(raw) }
            .getOrElse { PersistedState() }
    }

    suspend fun save(state: PersistedState) {
        dataStore.edit { it[STATE] = json.encodeToString(state) }
    }

    companion object {
        private val STATE = stringPreferencesKey("treinwijzer_state_v1")
    }
}
