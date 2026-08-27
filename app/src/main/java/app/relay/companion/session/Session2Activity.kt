package app.relay.companion.session

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.relay.companion.R
import app.relay.companion.data.PreferencesRepository
import app.relay.companion.data.ThemePreference
import app.relay.companion.data.resolveDark
import app.relay.companion.ui.session.SessionPane
import app.relay.companion.ui.theme.RelayTheme
import kotlinx.coroutines.flow.map

class Session2Activity : ComponentActivity() {

    private val sessionViewModel: SessionViewModel by viewModels()
    private val prefs by lazy { PreferencesRepository(applicationContext) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sessionViewModel.controller.loadHome()
        setContent {
            val theme by prefs.settings.map { it.theme }.collectAsState(initial = ThemePreference.System)
            val webTextZoom by prefs.settings.map { it.webTextZoom }
                .collectAsState(initial = PreferencesRepository.WEB_TEXT_ZOOM_DEFAULT)
            val dark = theme.resolveDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                sessionViewModel.controller.setDarkMode(dark)
            }
            RelayTheme(themePreference = theme) {
                BackHandler {
                    if (!sessionViewModel.controller.goBack()) finish()
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.session_two_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.cd_close),
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    SessionPane(
                        controller = sessionViewModel.controller,
                        listTextZoom = webTextZoom,
                        darkTheme = dark,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }
}
