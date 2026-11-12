package com.dynamicisland.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Reads real MediaSession data (title, artist, artwork, playback state, position).
 * Android only allows this for apps that hold notification access; the listener also reads title/text for island previews.
 * Event-driven: no polling.
 */
class MediaTracker(private val ctx: Context) {
    private val manager = ctx.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(ctx, MediaNotificationListener::class.java)
    private val _state = MutableStateFlow<MusicData?>(null)
    val state: StateFlow<MusicData?> = _state.asStateFlow()

    private var started = false
    private var attached = false
    private var tracked: List<MediaController> = emptyList()
    private var current: MediaController? = null

    private var artSource: Bitmap? = null
    private var artScaled: Bitmap? = null
    private val appCache = HashMap<String, Pair<String, Bitmap?>>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { onSessions(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        started = true
        attach()
    }

    fun stop() {
        started = false
        detach()
    }

    /** Called when the system binds/unbinds our listener, i.e. when notification access is granted/revoked. */
    fun onListenerChanged() {
        if (!started) return
        detach()
        attach()
    }

    fun playPause() {
        val c = current ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }
    fun previous() { current?.transportControls?.skipToPrevious() }

    private fun hasAccess() = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun attach() {
        if (attached || !hasAccess()) return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
            attached = true
            onSessions(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            Log.w(TAG, "Media access is not available (yet)", e)
        }
    }

    private fun detach() {
        if (attached) manager.removeOnActiveSessionsChangedListener(sessionsListener)
        attached = false
        tracked.forEach { it.unregisterCallback(callback) }
        tracked = emptyList()
        current = null
        _state.value = null
    }

    private fun onSessions(list: List<MediaController>) {
        val fresh = list.filter { it.packageName != ctx.packageName }
        val freshTokens = fresh.map { it.sessionToken }.toSet()
        tracked.filter { it.sessionToken !in freshTokens }.forEach { it.unregisterCallback(callback) }
        val known = tracked.associateBy { it.sessionToken }
        tracked = fresh.map { c ->
            known[c.sessionToken] ?: c.also { it.registerCallback(callback) }
        }
        refresh()
    }

    private fun refresh() {
        val chosen = tracked.firstOrNull {
            val s = it.playbackState?.state
            s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
        } ?: tracked.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        current = chosen
        _state.value = chosen?.let(::build)
    }

    private fun build(c: MediaController): MusicData {
        val md = c.metadata
        val ps = c.playbackState
        val (appName, appIcon) = appInfo(c.packageName)
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val rawArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val actions = ps?.actions ?: 0L
        val state = ps?.state
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        return MusicData(
            packageName = c.packageName,
            appName = appName,
            appIcon = appIcon,
            title = title?.takeIf { it.isNotBlank() } ?: appName,
            artist = artist.orEmpty(),
            artwork = artwork(rawArt),
            playing = playing,
            basePositionMs = ps?.position?.takeIf { it >= 0 } ?: 0L,
            baseElapsed = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = speed,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
            canPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canToggle = actions and (PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE) != 0L,
        )
    }

    /** Downscales artwork once per distinct bitmap so playback-state updates never re-decode it. */
    private fun artwork(raw: Bitmap?): Bitmap? {
        if (raw == null) return null
        if (raw === artSource) return artScaled
        val soft = if (raw.config == Bitmap.Config.HARDWARE) raw.copy(Bitmap.Config.ARGB_8888, false) else raw
        val scaled = if (soft == null) null else {
            val longest = max(soft.width, soft.height)
            if (longest <= MAX_ART) soft
            else Bitmap.createScaledBitmap(soft, soft.width * MAX_ART / longest, soft.height * MAX_ART / longest, true)
        }
        artSource = raw
        artScaled = scaled
        return scaled
    }

    private fun appInfo(pkg: String): Pair<String, Bitmap?> = appCache.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString() to pm.getApplicationIcon(info).toBitmap(96, 96)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "App info not visible for $pkg", e)
            pkg to null
        }
    }

    private companion object {
        const val TAG = "MediaTracker"
        const val MAX_ART = 320
    }
}
