package android.content

import android.content.pm.PackageManager

/**
 * Desktop stand-in for the tiny part of Android's Context that shared modules use
 * (only `Paxsenix.init()` reads the app version from it). Lets those modules compile
 * unchanged on the desktop so they can keep being updated from upstream.
 */
open class Context(
    open val packageName: String = "echo.music.desktop",
    versionName: String = "desktop",
) {
    open val packageManager: PackageManager = PackageManager(versionName)
}
