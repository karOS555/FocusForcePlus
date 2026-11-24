package com.focusforceplus.app.compliance

/**
 * A short-video feed inside a bigger app. The feed can be left while the rest of
 * the app keeps working, which takes the separate short-video service: it has to
 * see that the feed's player is on screen.
 *
 * [playerIds] are the app's own screen ids of the full-screen player. They change
 * with the app's updates now and then; when one does, the service finds nothing and
 * does nothing. It never guesses.
 */
enum class ShortFeed(
    val packageName: String,
    /** "Instagram Reels" */
    val label: String,
    /** "Reels", for sentences. */
    val noun: String,
    val playerIds: List<String>,
) {
    INSTAGRAM(
        packageName = "com.instagram.android",
        label = "Instagram Reels",
        noun = "Reels",
        playerIds = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_root",
            "root_clips_layout",
            "clips_video_container",
        ),
    ),
    YOUTUBE(
        packageName = "com.google.android.youtube",
        label = "YouTube Shorts",
        noun = "Shorts",
        playerIds = listOf(
            "reel_player_page_container",
            "shorts_player_container",
            "reel_watch_player",
            "reel_recycler",
        ),
    ),
    ;

    /**
     * The name a feed goes by as a member of a block, where every member is known
     * by a package name. No real package can be called this (the slash), so the
     * app blocker never mistakes it for an app: a rule under this key is carried
     * out by the short-video service alone.
     */
    val ruleKey: String get() = "$packageName/${noun.lowercase()}"

    companion object {
        fun fromRuleKey(key: String): ShortFeed? = entries.firstOrNull { it.ruleKey == key }
    }
}

/**
 * Apps that are nothing but short videos. There is no rest of the app to keep, so
 * the short-video section offers them as what they are, a block of the whole app.
 * That is done by the normal blocker, which only learns which app is in front; the
 * short-video service never sees these apps.
 *
 * Only apps a user can start are visible to FocusForce+ (the launcher query in the
 * manifest), so an entry for an app that is not installed simply never shows.
 */
val SHORT_VIDEO_ONLY_APPS: Set<String> = setOf(
    "com.zhiliaoapp.musically",       // TikTok
    "com.ss.android.ugc.trill",       // TikTok (Asia)
    "com.zhiliaoapp.musically.go",    // TikTok Lite
    "com.ss.android.ugc.trill.go",    // TikTok Lite (Asia)
    "com.tiktok.lite.go",             // TikTok Lite
    "com.ss.android.ugc.aweme",       // Douyin
    "com.ss.android.ugc.aweme.lite",  // Douyin Lite
    "com.smile.gifmaker",             // Kuaishou
    "com.kuaishou.nebula",            // Kuaishou Lite
    "com.kwai.video",                 // Kwai
    "com.kwai.bulldog",               // SnackVideo
    "video.like",                     // Likee
    "video.like.lite",                // Likee Lite
    "co.triller.droid",               // Triller
    "in.mohalla.video",               // Moj
    "com.eterno.shortvideos",         // Josh
    "io.chingari.app",                // Chingari
    "com.next.innovation.takatak",    // MX TakaTak
    "com.clapper.video",              // Clapper
    "com.vk.clips",                   // VK Clips
    "com.lomotif.android",            // Lomotif
    "com.funnypuri.client",           // Zili
    "com.roposo.android",             // Roposo
)

/** The daily times offered for short videos, in minutes. */
val SHORT_VIDEO_ALLOWANCES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

/**
 * How much short video a day is fine before the feeds are left again.
 * [minutes] 0 means none: a feed is left as soon as it opens. [each] gives every
 * feed its own time; otherwise all of them use up the same one.
 */
data class ShortVideoAllowance(val minutes: Int = 0, val each: Boolean = false) {
    val none: Boolean get() = minutes <= 0
}

/**
 * Today's time in the feeds' players, as the short-video service counted it. [day]
 * is the local day the numbers belong to; on any other day they count as zero.
 */
data class ShortVideoUsage(val day: Long = 0L, val millis: Map<ShortFeed, Long> = emptyMap()) {

    /** The usage as it stands on [today]: unchanged, or empty if the day is over. */
    fun on(today: Long): ShortVideoUsage = if (day == today) this else ShortVideoUsage(today)

    fun plus(feed: ShortFeed, add: Long, today: Long): ShortVideoUsage {
        val base = on(today)
        if (add <= 0L) return base
        return base.copy(millis = base.millis + (feed to (base.millis[feed] ?: 0L) + add))
    }

    fun minutes(feed: ShortFeed): Int = ((millis[feed] ?: 0L) / 60_000L).toInt()

    val totalMinutes: Int get() = (millis.values.sum() / 60_000L).toInt()
}

/** Whether the time for [feed] is used up, so its player has to be left. */
fun shortVideoTimeIsUp(feed: ShortFeed, allowance: ShortVideoAllowance, usage: ShortVideoUsage): Boolean {
    if (allowance.none) return true
    val used = if (allowance.each) usage.millis[feed] ?: 0L else usage.millis.values.sum()
    return used >= allowance.minutes * 60_000L
}

/**
 * Whether going from [from] to [to] allows more short video than before. More is a
 * loosening of a block and goes through the same speed bump as pausing one; less
 * is always free.
 */
fun loosensShortVideos(from: ShortVideoAllowance, to: ShortVideoAllowance): Boolean {
    if (to.none) return false
    if (from.none) return true
    return to.minutes > from.minutes || (to.each && !from.each)
}
