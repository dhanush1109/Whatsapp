package app.relay.companion

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.webkit.WebView
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

class RelayApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (isSession2Process()) {
            WebView.setDataDirectorySuffix("session2")
        }
        if (Build.VERSION.SDK_INT >= 28 && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }

    fun isSession2Process(): Boolean = currentProcessName().endsWith(":session2")

    fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= 28) {
            return getProcessName()
        }
        val pid = android.os.Process.myPid()
        val manager = getSystemService<ActivityManager>()
        return manager?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
            ?: packageName
    }

    companion object {
        lateinit var instance: RelayApp
            private set
    }
}
