package com.hwb.aianswerer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that starts the permission flow or stops the active assistant. */
class ChatAssistantTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // The Intent overload is required below API 34; the API 34+ branch uses PendingIntent.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (FloatingWindowService.isRunning) {
            stopService(Intent(this, FloatingWindowService::class.java))
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
            return
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_START_FROM_QUICK_TILE, true)
        }
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    20,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }
    }

    private fun updateTile() {
        qsTile?.apply {
            state = if (FloatingWindowService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.quick_tile_label)
            updateTile()
        }
    }

    companion object {
        fun refresh(context: Context) {
            TileService.requestListeningState(context, ComponentName(context, ChatAssistantTileService::class.java))
        }
    }
}
