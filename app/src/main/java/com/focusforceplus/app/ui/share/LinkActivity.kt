package com.focusforceplus.app.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.focusforceplus.app.MainActivity
import com.focusforceplus.app.ui.navigation.SettingsRoutes
import com.focusforceplus.app.util.ExternalStarts
import com.focusforceplus.app.util.StartLink
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives `focusforceplus://` start links from automation apps, home-screen
 * shortcuts and NFC tags, runs them and closes again. It has no screen of its own;
 * a short message says what happened.
 */
@AndroidEntryPoint
class LinkActivity : ComponentActivity() {

    @Inject lateinit var starts: ExternalStarts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data?.let(StartLink::parse)
        if (link == null) {
            say("FocusForce+ does not know this link.")
            finish()
            return
        }
        lifecycleScope.launch {
            when (val result = starts.run(link)) {
                is ExternalStarts.Result.Done -> say(result.message)
                is ExternalStarts.Result.Refused -> say(result.message)
                ExternalStarts.Result.Disabled -> {
                    say("Start links are off. You can switch them on here.")
                    startActivity(
                        Intent(this@LinkActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_OPEN_ROUTE, SettingsRoutes.INTEGRATIONS),
                    )
                }
            }
            finish()
        }
    }

    private fun say(message: String) = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}
