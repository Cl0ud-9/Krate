package dev.cl0ud9.krate

import android.app.Activity
import android.content.Intent
import android.os.Bundle

// "Suggest to Krate" in the share sheet: passes what was shared on to Krate's own window and closes, so Krate opens in
// its own task instead of a second copy inside the app the link was shared from
class ShareTargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action == Intent.ACTION_SEND && !text.isNullOrBlank()) {
            startActivity(
                Intent(this, KrateActivity::class.java)
                    // CLEAR_TOP with SINGLE_TOP hands it to the running screen even when Krate's task is already open
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ).putExtra(EXTRA_SHARED_TEXT, text),
            )
        }
        finish()
    }
}
