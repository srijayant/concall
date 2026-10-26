package com.aiconference.app

import android.app.Application
import com.aiconference.app.tiles.TileManager

class ConferenceApp : Application() {

    lateinit var tileManager: TileManager
        private set

    override fun onCreate() {
        super.onCreate()
        tileManager = TileManager(this)
    }

    companion object {
        fun tileManager(context: android.content.Context): TileManager =
            (context.applicationContext as ConferenceApp).tileManager
    }
}
