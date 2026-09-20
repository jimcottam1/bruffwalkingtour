package com.example.bruffwalkingtour

import android.app.Activity
import androidx.appcompat.app.AlertDialog

/**
 * The "Exit" action available on every screen, so a user is never stuck and can
 * always close the app and start again.
 *
 * With no walk in progress it just closes. Part-way round, it asks whether to
 * forget the walk (next launch starts fresh) or keep their place (next launch
 * resumes), since the tour otherwise silently resumes for 45 minutes.
 */
object ExitHelper {

    fun confirmExit(activity: Activity) {
        val progress = LocationService(activity)
        if (!progress.hasActiveSession()) {
            activity.finishAffinity()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.exit_start_over) { _, _ ->
                progress.clearSavedProgress()
                activity.finishAffinity()
            }
            .setNegativeButton(R.string.exit_keep_place) { _, _ -> activity.finishAffinity() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
