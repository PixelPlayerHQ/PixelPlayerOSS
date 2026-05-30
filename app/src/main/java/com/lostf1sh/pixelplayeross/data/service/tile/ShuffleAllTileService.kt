package com.lostf1sh.pixelplayeross.data.service.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.lostf1sh.pixelplayeross.MainActivity
import com.lostf1sh.pixelplayeross.MainActivityIntentContract

/**
 * Quick Settings tile that shuffles and plays all songs.
 * Fires ACTION_SHUFFLE_ALL to MainActivity, which calls PlayerViewModel.shuffleAllSongs().
 * Works whether the app is open or not.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ShuffleAllTileService : TileService() {

    companion object {
        private const val REQUEST_CODE_SHUFFLE_ALL = 1001
    }

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            setPackage(packageName)
            action = MainActivityIntentContract.ACTION_SHUFFLE_ALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivityAndCollapseCompat(
            intent = intent,
            requestCode = REQUEST_CODE_SHUFFLE_ALL
        )
    }
}
