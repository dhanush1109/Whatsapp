package app.relay.companion.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    val controller = SessionController(application)

    override fun onCleared() {
        controller.destroy()
        super.onCleared()
    }
}
