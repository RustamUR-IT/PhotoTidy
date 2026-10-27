package app.phototidy.core

/** The automatic folders on the home screen, in the order they're shown. */
enum class HomeFolder(val title: String) {
    CHATS("Chats"),
    SOCIAL("Social"),
    MEDIA("Media"),
    PHOTOS("Camera & Photos"),
    MONEY("Money"),
    SHOPPING("Shopping"),
    TRAVEL("Travel & Maps"),
    WORK("Work & Office"),
    GOOGLE("Google"),
    HEALTH("Health & Fitness"),
    NEWS("News & Reading"),
    GAMES("Games"),
    TOOLS("Tools"),
    SYSTEM("System");

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n }
    }
}

data class HomeApp(
    /** Unique key: package/activity. */
    val key: String,
    val pkg: String,
    val label: String,
    val category: Int = -1,
    val isSystem: Boolean = false,
)

object HomeRules {

    private val exact: Map<String, HomeFolder> = mapOf(
        "com.whatsapp" to HomeFolder.CHATS, "com.whatsapp.w4b" to HomeFolder.CHATS,
        "org.telegram.messenger" to HomeFolder.CHATS, "org.thoughtcrime.securesms" to HomeFolder.CHATS,
        "com.facebook.orca" to HomeFolder.CHATS, "com.discord" to HomeFolder.CHATS, "com.viber.voip" to HomeFolder.CHATS,
        "com.google.android.apps.messaging" to HomeFolder.CHATS, "com.google.android.gm" to HomeFolder.CHATS,
        "com.microsoft.office.outlook" to HomeFolder.CHATS,
        "com.instagram.android" to HomeFolder.SOCIAL, "com.facebook.katana" to HomeFolder.SOCIAL,
        "com.snapchat.android" to HomeFolder.SOCIAL, "com.zhiliaoapp.musically" to HomeFolder.SOCIAL,
        "com.twitter.android" to HomeFolder.SOCIAL, "com.reddit.frontpage" to HomeFolder.SOCIAL,
        "com.linkedin.android" to HomeFolder.SOCIAL, "com.pinterest" to HomeFolder.SOCIAL, "com.bereal.ft" to HomeFolder.SOCIAL,
        "com.spotify.music" to HomeFolder.MEDIA, "com.netflix.mediaclient" to HomeFolder.MEDIA,
        "com.disney.disneyplus" to HomeFolder.MEDIA, "com.amazon.avod.thirdpartyclient" to HomeFolder.MEDIA,
        "com.google.android.youtube" to HomeFolder.MEDIA, "com.google.android.apps.youtube.music" to HomeFolder.MEDIA,
        "com.shazam.android" to HomeFolder.MEDIA, "tv.twitch.android.app" to HomeFolder.MEDIA,
        "com.soundcloud.android" to HomeFolder.MEDIA,
        "com.google.android.apps.photos" to HomeFolder.PHOTOS,
        "com.paypal.android.p2pmobile" to HomeFolder.MONEY, "com.revolut.revolut" to HomeFolder.MONEY,
        "de.number26.android" to HomeFolder.MONEY, "com.google.android.apps.walletnfcrel" to HomeFolder.MONEY,
        "com.google.android.apps.authenticator2" to HomeFolder.MONEY, "com.azure.authenticator" to HomeFolder.MONEY,
        "com.amazon.mShop.android.shopping" to HomeFolder.SHOPPING,
        "com.google.android.apps.maps" to HomeFolder.TRAVEL, "com.ubercab" to HomeFolder.TRAVEL,
        "com.booking" to HomeFolder.TRAVEL, "com.airbnb.android" to HomeFolder.TRAVEL,
        "com.ubercab.eats" to HomeFolder.SHOPPING,
        "com.microsoft.teams" to HomeFolder.WORK, "com.microsoft.office.officehubrow" to HomeFolder.WORK,
        "us.zoom.videomeetings" to HomeFolder.WORK, "com.notion.id" to HomeFolder.WORK,
        "com.google.android.apps.docs" to HomeFolder.WORK, "com.google.android.calendar" to HomeFolder.WORK,
        "com.google.android.keep" to HomeFolder.WORK,
        "com.duolingo" to HomeFolder.NEWS,
        "com.android.vending" to HomeFolder.SYSTEM, "com.android.settings" to HomeFolder.SYSTEM,
    )

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    /** Picks the folder for an app: user's choice first, then known apps, keywords, Android's category. */
    fun folderFor(a: HomeApp, overrides: Map<String, HomeFolder> = emptyMap()): HomeFolder {
        overrides[a.pkg]?.let { return it }
        exact[a.pkg]?.let { return it }
        val t = (a.pkg + " " + a.label).lowercase()
        return when {
            has(t, "password", "bitwarden", "keepass", "lastpass", "vpn") -> HomeFolder.TOOLS
            has(t, "camera", "kamera", "gallery", "galerie", "photo", "foto", "snapseed", "lightroom", "picsart", "vsco") -> HomeFolder.PHOTOS
            has(t, "messag", "whatsapp", "telegram", "signal", "chat", "mail", "sms", "threema", "wire", "skype") -> HomeFolder.CHATS
            has(t, "facebook", "instagram", "tiktok", "snapchat", "twitter", "reddit", "threads", "mastodon",
                "bluesky", "tinder", "bumble", "hinge", "dating", "pinterest", "tumblr") -> HomeFolder.SOCIAL
            has(t, "bank", "erstebank", "raiffeisen", "sparkasse", "volksbank", "bawag", "george", "elba", "paypal", "revolut",
                "klarna", "wallet", "authenticator", "2fa", "bitpanda", "coinbase", "binance", "traderepublic", "finanz", "pay") -> HomeFolder.MONEY
            has(t, "oebb", "wienerlinien", "wiener linien", "maps", "navi", "flight", "flug", "airline", "austrian", "ryanair",
                "booking", "hotel", "taxi", "bolt", "uber", "rail", "bahn", "transit", "scooter", "lime", "tier", "travel",
                "trip", "weather", "wetter") -> HomeFolder.TRAVEL
            has(t, "shop", "amazon", "ebay", "willhaben", "zalando", "shein", "temu", "aliexpress", "ikea", "lidl", "billa",
                "spar", "hofer", "mjam", "lieferando", "foodora", "wolt", "deals", "coupon") -> HomeFolder.SHOPPING
            has(t, "fitness", "health", "gesund", "workout", "strava", "garmin", "fitbit", "sleep", "yoga", "running",
                "meditation", "calm", "headspace", "oegk", "arzt", "doctor", "pharma", "apotheke") -> HomeFolder.HEALTH
            has(t, "news", "zeitung", "derstandard", "krone", "orf", "kurier", "presse", "kindle", "book", "reader",
                "podcast", "learn", "duolingo", "wiki") -> HomeFolder.NEWS
            has(t, "music", "spotify", "video", "player", "tv", "stream", "radio", "netflix", "youtube", "audio") -> HomeFolder.MEDIA
            has(t, "office", "word", "excel", "powerpoint", "docs", "sheets", "slides", "calendar", "kalender", "notes",
                "notiz", "todo", "task", "teams", "slack", "zoom", "meet", "drive", "dropbox", "onedrive", "scanner", "pdf") -> HomeFolder.WORK
            a.category == 0 -> HomeFolder.GAMES
            a.category == 1 || a.category == 2 -> HomeFolder.MEDIA
            a.category == 3 -> HomeFolder.PHOTOS
            a.category == 4 -> HomeFolder.SOCIAL
            a.category == 5 -> HomeFolder.NEWS
            a.category == 6 -> HomeFolder.TRAVEL
            a.category == 7 -> HomeFolder.WORK
            a.pkg.startsWith("com.google.") -> HomeFolder.GOOGLE
            has(t, "game", "spiel", "puzzle", "chess", "schach") -> HomeFolder.GAMES
            a.isSystem || a.pkg.startsWith("com.android.") || a.pkg.startsWith("com.nothing.") -> HomeFolder.SYSTEM
            else -> HomeFolder.TOOLS
        }
    }

    /** Groups apps into folders (alphabetical inside), leaving out the dock apps. */
    fun arrange(
        apps: List<HomeApp>,
        dockKeys: List<String>,
        overrides: Map<String, HomeFolder> = emptyMap(),
    ): Map<HomeFolder, List<HomeApp>> {
        val dock = dockKeys.toSet()
        return apps.filter { it.key !in dock }
            .groupBy { folderFor(it, overrides) }
            .mapValues { (_, v) -> v.sortedBy { it.label.lowercase() } }
            .toSortedMap(compareBy { it.ordinal })
    }

    /**
     * Dock = 4 apps: the most used ones if we know usage, topped up with the
     * default phone, messages, browser and camera apps.
     */
    fun pickDock(
        apps: List<HomeApp>,
        usageMinutesByPkg: Map<String, Long>,
        defaults: List<String>,
        size: Int = 4,
    ): List<String> {
        val byPkg = apps.groupBy { it.pkg }
        val out = LinkedHashSet<String>()
        usageMinutesByPkg.entries.sortedByDescending { it.value }
            .forEach { (pkg, _) -> if (out.size < size) byPkg[pkg]?.firstOrNull()?.let { out += it.key } }
        defaults.forEach { pkg -> if (out.size < size) byPkg[pkg]?.firstOrNull()?.let { out += it.key } }
        return out.toList()
    }

    /** Most-used first (minutes over the last weeks), then alphabetical. */
    fun byUsage(apps: List<HomeApp>, usageMinutesByPkg: Map<String, Long>): List<HomeApp> =
        apps.sortedWith(
            compareByDescending<HomeApp> { usageMinutesByPkg[it.pkg] ?: 0L }.thenBy { it.label.lowercase() }
        )

    /** Apps of one folder, most-used first (for the folder widget). */
    fun folderApps(
        apps: List<HomeApp>,
        folder: HomeFolder,
        usageMinutesByPkg: Map<String, Long>,
        overrides: Map<String, HomeFolder> = emptyMap(),
    ): List<HomeApp> = byUsage(apps.filter { folderFor(it, overrides) == folder }, usageMinutesByPkg)

    data class Row(val title: String, val folder: HomeFolder?, val apps: List<HomeApp>)

    /**
     * Rows for the dashboard widget: "Most used" first (if usage is known),
     * then every non-empty folder, each sorted by usage. An app shown under
     * "Most used" is not repeated below.
     */
    fun dashboardRows(
        apps: List<HomeApp>,
        usageMinutesByPkg: Map<String, Long>,
        overrides: Map<String, HomeFolder> = emptyMap(),
        perRow: Int = 5,
    ): List<Row> {
        val rows = mutableListOf<Row>()
        val top = byUsage(apps.filter { (usageMinutesByPkg[it.pkg] ?: 0L) > 0 }, usageMinutesByPkg)
            .distinctBy { it.pkg }.take(perRow)
        if (top.isNotEmpty()) rows += Row("Most used", null, top)
        val shown = top.map { it.key }.toSet()
        arrange(apps, shown.toList(), overrides).forEach { (folder, members) ->
            if (members.isNotEmpty()) rows += Row(folder.title, folder, byUsage(members, usageMinutesByPkg))
        }
        return rows
    }
}
