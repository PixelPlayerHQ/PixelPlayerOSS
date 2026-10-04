package com.lostf1sh.pixelplayeross.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/** Receives [PackageInstaller] session results for self-updates. */
class UpdateInstallReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdateInstallReceiverEntryPoint {
        fun appUpdateManager(): AppUpdateManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && confirmIntent != null) {
            // Succeeds while the app is in the foreground; otherwise the Updates screen keeps
            // the intent and offers to reopen the system prompt.
            runCatching { context.startActivity(Intent(confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Timber.w(it, "Could not open the install confirmation") }
        }

        EntryPointAccessors.fromApplication(context, UpdateInstallReceiverEntryPoint::class.java)
            .appUpdateManager()
            .onInstallStatus(status, confirmIntent, message)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.lostf1sh.pixelplayeross.action.UPDATE_INSTALL_STATUS"
    }
}
