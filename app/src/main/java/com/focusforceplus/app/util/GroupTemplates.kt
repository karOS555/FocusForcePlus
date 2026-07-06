package com.focusforceplus.app.util

/**
 * Suggested blocker-group templates. Each maps a name to well-known package
 * prefixes; when the user picks a template while creating a group, the matching
 * installed apps are pre-selected (nothing is created without confirmation).
 */
object GroupTemplates {

    data class Template(val name: String, val packagePrefixes: List<String>)

    val ALL = listOf(
        Template(
            name = "Social Media",
            packagePrefixes = listOf(
                "com.instagram", "com.facebook.katana", "com.facebook.lite",
                "com.twitter", "com.zhiliaoapp.musically", "com.ss.android.ugc",
                "com.snapchat", "com.reddit", "com.pinterest", "com.linkedin",
                "com.tumblr", "com.vk", "com.bereal",
            ),
        ),
        Template(
            name = "Games",
            packagePrefixes = listOf(
                "com.supercell", "com.king.", "com.mojang", "com.roblox",
                "com.epicgames", "com.miHoYo", "com.HoYoverse", "com.activision",
                "com.ea.", "com.gameloft", "com.rockstargames", "com.nintendo",
                "com.dts.freefire", "com.tencent.ig", "com.innersloth",
            ),
        ),
        Template(
            name = "Entertainment",
            packagePrefixes = listOf(
                "com.google.android.youtube", "com.netflix", "com.amazon.avod",
                "com.disney.disneyplus", "tv.twitch", "com.spotify",
                "com.soundcloud", "com.crunchyroll", "com.hulu", "de.prosiebensat1digital",
            ),
        ),
    )

    /** Installed apps matching [template], for pre-selection in the group editor. */
    fun matchInstalled(template: Template, installed: List<InstalledApp>): List<InstalledApp> =
        installed.filter { app ->
            template.packagePrefixes.any { prefix -> app.packageName.startsWith(prefix) }
        }
}
