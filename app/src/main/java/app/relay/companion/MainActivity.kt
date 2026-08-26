package app.relay.companion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.relay.companion.session.SessionViewModel
import app.relay.companion.ui.shell.RelayRoot

class MainActivity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }
        splashScreen.setOnExitAnimationListener { provider ->
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f).apply {
                duration = 220
            }
            val scaleX = ObjectAnimator.ofFloat(provider.iconView, View.SCALE_X, 1f, 1.15f).apply {
                duration = 220
            }
            val scaleY = ObjectAnimator.ofFloat(provider.iconView, View.SCALE_Y, 1f, 1.15f).apply {
                duration = 220
            }
            AnimatorSet().apply {
                playTogether(fade, scaleX, scaleY)
                doOnEnd { provider.remove() }
                start()
            }
        }

        setContent {
            RelayRoot(sessionViewModel, onReady = { isReady = true })
        }
    }
}
