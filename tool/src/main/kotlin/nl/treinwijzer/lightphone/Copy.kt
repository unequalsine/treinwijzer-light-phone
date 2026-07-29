package nl.treinwijzer.lightphone

class Copy(private val language: Language) {
    private fun value(en: String, nl: String) = if (language == Language.ENGLISH) en else nl

    val app = "Treinwijzer"
    val loading get() = value("Loading…", "Laden…")
    val networkError get() = value("Could not reach Treinwijzer. Check your connection and configuration.", "Treinwijzer is niet bereikbaar. Controleer je verbinding en configuratie.")
    val searchStation get() = value("Search station", "Station zoeken")
    val noResults get() = value("No stations found.", "Geen stations gevonden.")
    val departures get() = value("Departures", "Vertrektijden")
    val disruptions get() = value("Disruptions", "Storingen")
    val planner get() = value("Plan journey", "Reis plannen")
    val favourites get() = value("Favourites", "Favorieten")
    val settings get() = value("Settings", "Instellingen")
    val nearest get() = value("Nearest stations", "Stations in de buurt")
    val nearestUnavailable get() = value(
        "Nearest stations are not supported by the current Light SDK because tools have no location access.",
        "Stations in de buurt worden niet ondersteund door de huidige Light SDK, omdat tools geen locatietoegang hebben.",
    )
    val activeJourney get() = value("Active journey", "Actieve reis")
    val origin get() = value("From", "Van")
    val destination get() = value("To", "Naar")
    val via get() = value("Via (optional)", "Via (optioneel)")
    val leaving get() = value("Depart", "Vertrek")
    val arriving get() = value("Arrive", "Aankomst")
    val now get() = value("Now", "Nu")
    val chooseTime get() = value("Date and time", "Datum en tijd")
    val dateTimeHelp get() = value("Enter YYYY-MM-DD HH:mm", "Voer JJJJ-MM-DD UU:mm in")
    val plan get() = value("Plan", "Plan")
    val refresh get() = value("Refresh", "Vernieuwen")
    val track get() = value("Track journey", "Reis volgen")
    val stopTracking get() = value("Stop tracking", "Stop volgen")
    val recovery get() = value("Recovery journeys", "Alternatieve reizen")
    val noJourneys get() = value("No journeys found.", "Geen reizen gevonden.")
    val noDepartures get() = value("No departures found.", "Geen vertrektijden gevonden.")
    val noDisruptions get() = value("No current disruptions.", "Geen actuele storingen.")
    val addFavourite get() = value("Add favourite", "Favoriet toevoegen")
    val removeFavourite get() = value("Remove favourite", "Favoriet verwijderen")
    val saveRoute get() = value("Save route", "Route bewaren")
    val recent get() = value("Recent", "Recent")
    val favouriteStations get() = value("Favourite stations", "Favoriete stations")
    val favouriteRoutes get() = value("Favourite routes", "Favoriete routes")
    val more get() = value("More", "Meer")
    val alerts get() = value("Journey alerts", "Reismeldingen")
    val guidance get() = value("Transfer guidance", "Overstapbegeleiding")
    val english get() = "English"
    val dutch get() = "Nederlands"
    val enabled get() = value("On", "Aan")
    val disabled get() = value("Off", "Uit")
    val back get() = value("Back", "Terug")
    val close get() = value("Close", "Sluiten")
    val retry get() = value("Retry", "Opnieuw")
    val platform get() = value("Platform", "Spoor")
    val cancelled get() = value("Cancelled", "Opgeheven")
    val transfer get() = value("transfer", "overstap")
    val transfers get() = value("transfers", "overstappen")
    val minutes get() = value("min", "min")
    val price get() = value("Price", "Prijs")
    val live get() = value("Live", "Live")
    val configured get() = value("Connected", "Verbonden")
    val notConfigured get() = value("Worker token is not configured.", "Worker-token is niet ingesteld.")
    val notificationsLimited get() = value(
        "Background tracking works through Light push. The current SDK cannot show a system alert while the tool is closed; missed alerts appear here on next launch.",
        "Volgen op de achtergrond werkt via Light-push. De huidige SDK kan geen systeemmelding tonen als de tool gesloten is; gemiste meldingen verschijnen bij de volgende start.",
    )
    val invalidDate get() = value("Use YYYY-MM-DD HH:mm.", "Gebruik JJJJ-MM-DD UU:mm.")
}
