package app.phototidy.core

/** Everything the rules need to know about one installed app (pure data, testable). */
data class AppFacts(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    /** ApplicationInfo.category value, or -1 if unknown. */
    val category: Int = -1,
    /** The app's own description from its manifest, if it has one. */
    val ownDescription: String? = null,
    /** Default home screen, phone, SMS, browser or keyboard app. */
    val defaultRole: String? = null,
    val isDeviceAdmin: Boolean = false,
    val installedMs: Long = 0L,
    /** Last time opened; null = not opened in the period Android keeps records for. */
    val lastUsedMs: Long? = null,
    /** True when we have usage access at all (otherwise lastUsedMs means nothing). */
    val usageKnown: Boolean = false,
)

enum class Importance(val title: String) {
    /** Phone stops working properly without it. */
    ESSENTIAL("Essential"),
    /** Removing it can lock you out of money, accounts or data. */
    IMPORTANT("Important"),
    NORMAL("Normal"),
}

data class AppVerdict(
    val importance: Importance,
    /** What the app does, in one line. */
    val whatItDoes: String,
    /** The reminder shown before removing it (null for normal apps). */
    val reminder: String?,
)

object AppRules {

    private data class Known(val what: String, val importance: Importance = Importance.NORMAL, val reminder: String? = null)

    private const val CHATS = "Your chats are stored on this phone. Back them up inside the app first, or they're gone."
    private const val TWOFA = "This app makes login codes. Move your accounts to another device first, or you can get locked out of them."
    private const val MONEY = "Banking/payment app. Make sure you know your login and have another way to confirm payments before removing it."
    private const val MAIL = "Email is often how you reset passwords. You can always reinstall it, but make sure you know the login."
    private const val PASSWORDS = "Your passwords live here. Make sure they're synced to your account or exported before removing it."

    /** Well-known apps by package name. */
    private val known: Map<String, Known> = mapOf(
        // messengers
        "com.whatsapp" to Known("WhatsApp – chats and calls", Importance.IMPORTANT, CHATS),
        "com.whatsapp.w4b" to Known("WhatsApp Business – chats with customers", Importance.IMPORTANT, CHATS),
        "org.thoughtcrime.securesms" to Known("Signal – private chats and calls", Importance.IMPORTANT, CHATS),
        "org.telegram.messenger" to Known("Telegram – chats and channels (stored in the cloud)"),
        "com.facebook.orca" to Known("Messenger – Facebook chats"),
        "com.discord" to Known("Discord – group chats and voice channels"),
        "com.viber.voip" to Known("Viber – chats and calls", Importance.IMPORTANT, CHATS),
        // social
        "com.instagram.android" to Known("Instagram – photos, reels and stories"),
        "com.facebook.katana" to Known("Facebook – social network"),
        "com.snapchat.android" to Known("Snapchat – photo messages and stories"),
        "com.zhiliaoapp.musically" to Known("TikTok – short videos"),
        "com.twitter.android" to Known("X (Twitter) – short posts and news"),
        "com.reddit.frontpage" to Known("Reddit – forums and discussions"),
        "com.linkedin.android" to Known("LinkedIn – job and business network"),
        "com.pinterest" to Known("Pinterest – ideas and inspiration boards"),
        "com.bereal.ft" to Known("BeReal – one daily photo with friends"),
        // media
        "com.spotify.music" to Known("Spotify – music and podcasts"),
        "com.netflix.mediaclient" to Known("Netflix – films and series"),
        "com.disney.disneyplus" to Known("Disney+ – films and series"),
        "com.amazon.avod.thirdpartyclient" to Known("Prime Video – films and series"),
        "com.google.android.youtube" to Known("YouTube – videos"),
        "com.google.android.apps.youtube.music" to Known("YouTube Music – music streaming"),
        "com.shazam.android" to Known("Shazam – recognises songs playing around you"),
        "com.soundcloud.android" to Known("SoundCloud – music streaming"),
        "tv.twitch.android.app" to Known("Twitch – live streams"),
        // google & productivity
        "com.google.android.gm" to Known("Gmail – email", Importance.IMPORTANT, MAIL),
        "com.microsoft.office.outlook" to Known("Outlook – email and calendar", Importance.IMPORTANT, MAIL),
        "com.google.android.apps.maps" to Known("Google Maps – maps and navigation"),
        "com.google.android.apps.photos" to Known("Google Photos – photo backup and gallery", Importance.IMPORTANT,
            "Check that your photos finished backing up before removing it."),
        "com.google.android.calendar" to Known("Google Calendar – your calendar"),
        "com.google.android.keep" to Known("Google Keep – notes and lists"),
        "com.google.android.apps.docs" to Known("Google Drive – cloud files"),
        "com.google.android.apps.translate" to Known("Google Translate – translation"),
        "com.microsoft.teams" to Known("Microsoft Teams – work chats and meetings"),
        "com.microsoft.office.officehubrow" to Known("Microsoft 365 – Word, Excel and PowerPoint"),
        "us.zoom.videomeetings" to Known("Zoom – video meetings"),
        "com.notion.id" to Known("Notion – notes and workspaces"),
        "com.duolingo" to Known("Duolingo – language learning"),
        // 2FA / passwords
        "com.google.android.apps.authenticator2" to Known("Google Authenticator – login codes", Importance.IMPORTANT, TWOFA),
        "com.azure.authenticator" to Known("Microsoft Authenticator – login codes", Importance.IMPORTANT, TWOFA),
        "com.authy.authy" to Known("Authy – login codes", Importance.IMPORTANT, TWOFA),
        "com.x8bit.bitwarden" to Known("Bitwarden – password manager", Importance.IMPORTANT, PASSWORDS),
        "com.onepassword.android" to Known("1Password – password manager", Importance.IMPORTANT, PASSWORDS),
        // money
        "com.paypal.android.p2pmobile" to Known("PayPal – payments", Importance.IMPORTANT, MONEY),
        "com.revolut.revolut" to Known("Revolut – bank account and cards", Importance.IMPORTANT, MONEY),
        "de.number26.android" to Known("N26 – bank account", Importance.IMPORTANT, MONEY),
        "com.google.android.apps.walletnfcrel" to Known("Google Wallet – tap-to-pay cards and tickets", Importance.IMPORTANT,
            "Your cards for paying by phone are stored here."),
        // shopping / travel
        "com.amazon.mShop.android.shopping" to Known("Amazon – shopping"),
        "com.ubercab" to Known("Uber – rides"),
        "com.ubercab.eats" to Known("Uber Eats – food delivery"),
        "com.booking" to Known("Booking.com – hotels and travel"),
        "com.airbnb.android" to Known("Airbnb – holiday stays"),
        // browsers
        "com.android.chrome" to Known("Chrome – web browser"),
        "org.mozilla.firefox" to Known("Firefox – web browser"),
        "com.brave.browser" to Known("Brave – web browser"),
        "com.opera.browser" to Known("Opera – web browser"),
        // core
        "com.android.vending" to Known("Play Store – installs and updates apps", Importance.ESSENTIAL),
        "com.google.android.gms" to Known("Google Play services – keeps Google apps, location and notifications working", Importance.ESSENTIAL),
        "com.android.settings" to Known("Settings", Importance.ESSENTIAL),
        "com.google.android.inputmethod.latin" to Known("Gboard – keyboard"),
    )

    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    fun verdict(a: AppFacts): AppVerdict {
        // 1. Things the phone relies on right now
        a.defaultRole?.let { role ->
            return AppVerdict(
                Importance.ESSENTIAL,
                known[a.pkg]?.what ?: "${a.label} – your $role",
                "This is your $role. Pick another one first, otherwise Android has nothing to use for it.",
            )
        }
        if (a.isDeviceAdmin) {
            return AppVerdict(
                Importance.ESSENTIAL,
                known[a.pkg]?.what ?: describeFallback(a),
                "This app has device-admin rights (e.g. Find My Device, work profile). " +
                    "Remove those under Settings › Security › Device admin apps first.",
            )
        }

        // 2. Known apps
        known[a.pkg]?.let { k ->
            val imp = if (k.importance == Importance.NORMAL && a.isSystem) Importance.NORMAL else k.importance
            return AppVerdict(imp, k.what, k.reminder ?: systemReminder(a, imp))
        }

        // 3. Keyword guesses on package + name
        val t = (a.pkg + " " + a.label).lowercase()
        when {
            has(t, "authenticator", "2fa", "totp", "otp.", ".otp", "authy") ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – login codes (two-factor)", TWOFA)
            has(t, "bank", "erstebank", "raiffeisen", "sparkasse", "volksbank", "bawag", "elba", "george",
                "revolut", "paypal", "klarna", "traderepublic", "bitpanda", "coinbase", "binance", "broker", "finanz") ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – banking or money", MONEY)
            has(t, "password", "bitwarden", "lastpass", "keepass", "dashlane", "1password", "proton.pass") ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – password manager", PASSWORDS)
            has(t, "wallet") ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – wallet for cards or tickets",
                    "Cards, tickets or passes stored here may be gone after removing it.")
            has(t, "at.gv.", ".gv.at", "id austria", "digitales amt", "handysignatur", "handy-signatur") ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – official ID / government services",
                    "Official ID app. Re-activating it later can take a visit or a letter – keep it unless you're sure.")
            has(t, "vpn", "antivirus", "security") ->
                return AppVerdict(Importance.NORMAL, "${a.label} – security or VPN", null)
            has(t, "mail") && !a.isSystem ->
                return AppVerdict(Importance.IMPORTANT, "${a.label} – email", MAIL)
        }

        // 4. Android system packages
        if (a.pkg.startsWith("com.android.") || a.pkg.startsWith("android") ) {
            return AppVerdict(Importance.ESSENTIAL, a.ownDescription ?: "Part of Android", systemReminder(a, Importance.ESSENTIAL))
        }

        val what = describeFallback(a)
        return AppVerdict(Importance.NORMAL, what, systemReminder(a, Importance.NORMAL))
    }

    private fun systemReminder(a: AppFacts, imp: Importance): String? = when {
        !a.isSystem -> null
        imp == Importance.ESSENTIAL -> "Part of the phone's system. Disabling it can break other features."
        else -> "Pre-installed app. It can only be disabled, not removed – you can turn it back on anytime in Settings › Apps."
    }

    private fun describeFallback(a: AppFacts): String {
        a.ownDescription?.takeIf { it.isNotBlank() && it.length < 160 }?.let { return it.trim() }
        val kind = when (a.category) {
            0 -> "a game"
            1 -> "music or audio"
            2 -> "video"
            3 -> "photos or images"
            4 -> "social or chat"
            5 -> "news or magazines"
            6 -> "maps and navigation"
            7 -> "productivity"
            8 -> "accessibility"
            else -> null
        }
        return when {
            kind != null -> "${a.label} – $kind"
            a.isSystem -> "${a.label} – pre-installed app"
            else -> a.label
        }
    }

    /** Days without use after which an app is suggested. */
    fun unusedDays(s: Sensitivity) = when (s) {
        Sensitivity.CAREFUL -> 180L
        Sensitivity.NORMAL -> 90L
        Sensitivity.THOROUGH -> 30L
    }

    /**
     * Unused = installed longer than the limit and not opened within it.
     * Returns null when it's in use (or when we can't tell).
     */
    fun unusedForDays(a: AppFacts, s: Sensitivity, nowMs: Long): Long? {
        if (!a.usageKnown || a.isSystem) return null
        val limit = unusedDays(s)
        val day = 86_400_000L
        val since = a.lastUsedMs ?: a.installedMs
        if (since <= 0L) return null
        if ((nowMs - a.installedMs) / day < limit) return null
        val days = (nowMs - since) / day
        return if (days >= limit) days else null
    }
}
