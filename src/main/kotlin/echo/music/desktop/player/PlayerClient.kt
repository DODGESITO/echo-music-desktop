package echo.music.desktop.player

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

object PlayerClient {

    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private const val IOS_UA =
        "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

    private const val ANDROID_UA =
        "com.google.android.youtube/21.26.364 " +
            "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"

    private const val ANDROID_MUSIC_UA =
        "com.google.android.apps.youtube.music/8.39.42 " +
            "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip"

    private const val ANDROID_VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private const val TVHTML5_UA =
        "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 " +
            "(KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15"

    private const val VISIONOS_UA =
        "com.google.ios.youtube/1.02 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/605.1.15 (KHTML, like Gecko)"

    fun mediaHeadersFor(url: String): Map<String, String> {
        val parsed = url.toHttpUrlOrNull() ?: return mapOf("User-Agent" to IOS_UA)
        val name = parsed.queryParameter("c")?.uppercase(Locale.ROOT) ?: "IOS"

        var origin: String? = null

        val userAgent = when {
            name == "VISIONOS" -> VISIONOS_UA
            name == "ANDROID_VR" -> ANDROID_VR_UA
            name == "ANDROID_MUSIC" -> ANDROID_MUSIC_UA
            name.startsWith("ANDROID") -> ANDROID_UA
            name.startsWith("TVHTML5") -> {
                origin = YOUTUBE_ORIGIN
                TVHTML5_UA
            }
            name == "WEB_REMIX" -> {
                origin = MUSIC_ORIGIN
                WEB_USER_AGENT
            }
            name.startsWith("WEB") || name == "MWEB" -> {
                origin = YOUTUBE_ORIGIN
                WEB_USER_AGENT
            }
            name.startsWith("IOS") -> IOS_UA
            else -> IOS_UA
        }

        return buildMap {
            put("User-Agent", userAgent)
            origin?.let {
                put("Origin", it)
                put("Referer", "$it/")
            }
        }
    }
}
