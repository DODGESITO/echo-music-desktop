package android.content.pm

/** Desktop stand-in: always reports the desktop app version. */
class PackageManager(private val versionName: String) {
    @Suppress("UNUSED_PARAMETER")
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo(versionName)
}

class PackageInfo(@JvmField val versionName: String?)
