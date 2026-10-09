package nl.treinwijzer.lightphone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackendAndLocationTest {
    @Test
    fun nearestStationsRankValidCoordinatesAndLimitResults() {
        val stations = listOf(
            Station("RTD", "Rotterdam", "NL", 51.924, 4.469),
            Station("ASD", "Amsterdam", "NL", 52.379, 4.900),
            Station("UT", "Utrecht", "NL", 52.089, 5.110),
            Station("MISSING", "Missing", "NL"),
            Station("INVALID", "Invalid", "NL", Double.NaN, 4.900),
            Station("RANGE", "Out of range", "NL", 92.0, 4.900),
        )
        assertEquals(listOf("ASD", "UT", "RTD"), nearestStations(stations, 52.379, 4.900).map { it.code })
        assertTrue(nearestStations(stations, 91.0, 4.900).isEmpty())
        assertEquals(8, nearestStations((1..12).map { Station("$it", "$it", "NL", 52.0 + it / 100.0, 5.0) }, 52.0, 5.0).size)
    }

    @Test
    fun locationRejectsOldUnknownAndInaccurateFixes() {
        val now = 1_000_000L
        assertTrue(usableLocation(52.0, 5.0, 10.0, now - 2_000, now))
        assertFalse(usableLocation(52.0, 5.0, 10.0, now - 120_001, now))
        assertFalse(usableLocation(52.0, 5.0, 10.0, now + 1, now))
        assertFalse(usableLocation(52.0, 5.0, 5_001.0, now, now))
        assertFalse(usableLocation(null, 5.0, 10.0, now, now))
        assertFalse(usableLocation(52.0, Double.NaN, 10.0, now, now))
    }

    @Test
    fun productionMigrationNeverReusesDevelopmentCredentials() {
        val favourite = Station("UT", "Utrecht", "NL")
        val old = PersistedState(
            installId = "dev-install",
            installSecret = "dev-secret",
            registeredPushEndpoint = "https://push.example/dev",
            favouriteStations = listOf(favourite),
            activeJourney = kotlinx.serialization.json.Json.decodeFromString<TripOption>(
                checkNotNull(javaClass.classLoader.getResource("fixtures/trip-contract.json")).readText(),
            ),
        )
        val migrated = old.forBackend("https://treinwijzer-light.unequalsine.workers.dev/") { "new-install" }
        assertEquals("new-install", migrated.installId)
        assertNull(migrated.installSecret)
        assertNull(migrated.registeredPushEndpoint)
        assertNull(migrated.activeJourney)
        assertEquals(listOf(favourite), migrated.favouriteStations)
        assertEquals(migrated, migrated.forBackend("https://treinwijzer-light.unequalsine.workers.dev"))
        assertEquals("dev-secret", old.forBackend("https://treinwijzer-light-dev.unequalsine.workers.dev").installSecret)
    }

    @Test
    fun returningToDevelopmentAlsoCreatesAnIndependentIdentity() {
        val production = PersistedState(installId = "prod", installSecret = "prod-secret", serviceBaseUrl = "https://production.example")
        val development = production.forBackend("https://development.example") { "dev" }
        assertEquals("dev", development.installId)
        assertNull(development.installSecret)
    }
}
