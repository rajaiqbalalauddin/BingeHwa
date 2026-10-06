package com.raja.bingehwa

import android.app.Application
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.raja.bingehwa.data.cloud.Cloud
import com.raja.bingehwa.data.cloud.FirebaseCloud
import com.raja.bingehwa.data.cloud.NoCloud
import com.raja.bingehwa.data.local.AppDb
import com.raja.bingehwa.data.prefs.Settings
import com.raja.bingehwa.data.remote.MangaDexApi
import com.raja.bingehwa.data.repo.AniListClient
import com.raja.bingehwa.data.repo.Repo
import com.raja.bingehwa.push.Notifier
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-built dependency container. Why not Hilt: it adds a code generator for an app this
 * size, and one explicit class is easier to read and to keep compiling.
 */
class AppContainer(app: Application) {
    val settings = Settings(app)
    val db: AppDb = Room.databaseBuilder(app, AppDb::class.java, "bingehwa.db").build()
    val api = MangaDexApi()

    /** Plain client for image files: no API throttling, since images come from CDN hosts. */
    val imageClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val cloud: Cloud = try {
        if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseCloud(nameProvider = { settings.current.displayName }) else NoCloud()
    } catch (e: Exception) {
        NoCloud()
    }

    val repo = Repo(api, db, settings, cloud, AniListClient(imageClient), app)
}

class BingeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannels(this)
    }
}
