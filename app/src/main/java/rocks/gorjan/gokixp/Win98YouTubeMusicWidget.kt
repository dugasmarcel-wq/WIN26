package rocks.gorjan.gokixp

import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Desktop controller for the real YouTube Music media session.
 *
 * Playback stays in YouTube Music; WIN26 uses Android MediaSession controls and the
 * metadata YouTube Music already exposes to the operating system.
 */
class Win98YouTubeMusicWidget(
    context: Context,
    private val onOpenNotificationAccess: () -> Unit,
    private val onOpenYouTubeMusic: () -> Unit
) : FrameLayout(context) {

    companion object {
        private const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private const val PREF_X = "win98_music_widget_x"
        private const val PREF_Y = "win98_music_widget_y"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val listenerComponent =
        ComponentName(context, NotificationListenerService::class.java)

    private var controller: MediaController? = null
    private var trackingSeek = false
    private var attached = false

    private val albumArt: ImageView
    private val titleText: TextView
    private val artistText: TextView
    private val statusText: TextView
    private val timeText: TextView
    private val playPause: ImageButton
    private val progress: SeekBar
    private val volume: SeekBar
    private val shuffleButton: TextView
    private val repeatButton: TextView
    private val spectrum: SpectrumView

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            render()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            render()
        }

        override fun onSessionDestroyed() {
            disconnectController()
            render()
        }

        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) {
            renderVolume()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!attached) return
            refreshController()
            renderPosition()
            mainHandler.postDelayed(this, 850L)
        }
    }

    init {
        setWillNotDraw(false)
        background = shellBackground()
        clipToPadding = false
        elevation = dp(7).toFloat()

        val dragBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(7), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.rgb(43, 92, 18),
                    Color.rgb(145, 255, 25),
                    Color.rgb(43, 92, 18)
                )
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(23, 56, 8))
            }
        }
        dragBar.addView(
            TextView(context).apply {
                text = "WINSUNG // MUTANT MEDIA"
                setTextColor(Color.rgb(16, 45, 7))
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )
        val openButton = tinyButton("YTM").apply {
            setOnClickListener { onOpenYouTubeMusic() }
        }
        dragBar.addView(openButton, LinearLayout.LayoutParams(dp(45), dp(22)))
        addView(
            dragBar,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28), Gravity.TOP).apply {
                leftMargin = dp(5)
                rightMargin = dp(5)
                topMargin = dp(4)
            }
        )
        enableDragging(dragBar)

        addView(makeSpeakerColumn(), LayoutParams(dp(42), dp(112), Gravity.START).apply {
            leftMargin = dp(4)
            topMargin = dp(31)
        })
        addView(makeSpeakerColumn(), LayoutParams(dp(42), dp(112), Gravity.END).apply {
            rightMargin = dp(4)
            topMargin = dp(31)
        })

        val display = FrameLayout(context).apply {
            background = screenBackground()
            setPadding(dp(7), dp(7), dp(7), dp(7))
        }
        addView(
            display,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(91), Gravity.TOP).apply {
                leftMargin = dp(47)
                rightMargin = dp(47)
                topMargin = dp(33)
            }
        )

        albumArt = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 24, 38))
                setStroke(dp(1), Color.rgb(96, 255, 72))
                cornerRadius = dp(5).toFloat()
            }
        }
        display.addView(
            albumArt,
            LayoutParams(dp(68), dp(68), Gravity.START or Gravity.TOP)
        )

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        display.addView(
            info,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70), Gravity.TOP).apply {
                leftMargin = dp(72)
            }
        )

        titleText = TextView(context).apply {
            text = "YouTube Music"
            setTextColor(Color.rgb(174, 255, 73))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        info.addView(titleText)

        artistText = TextView(context).apply {
            text = "Open YTM to start"
            setTextColor(Color.rgb(81, 231, 235))
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        info.addView(artistText)

        statusText = TextView(context).apply {
            text = "MEDIA SESSION: STANDBY"
            setTextColor(Color.rgb(137, 163, 183))
            textSize = 8.5f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            setPadding(0, dp(5), 0, 0)
        }
        info.addView(statusText)

        spectrum = SpectrumView(context)
        display.addView(
            spectrum,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(13), Gravity.BOTTOM).apply {
                leftMargin = dp(73)
            }
        )

        progress = SeekBar(context).apply {
            max = 1000
            progress = 0
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    trackingSeek = true
                }

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) renderTimePreview(value)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val c = controller
                    val duration = mediaDuration()
                    if (c != null && duration > 0L) {
                        val target = duration * (seekBar?.progress ?: 0) / 1000L
                        c.transportControls.seekTo(target)
                    }
                    trackingSeek = false
                    renderPosition()
                }
            })
        }
        addView(
            progress,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24), Gravity.TOP).apply {
                leftMargin = dp(44)
                rightMargin = dp(44)
                topMargin = dp(124)
            }
        )

        timeText = TextView(context).apply {
            text = "00:00 / --:--"
            setTextColor(Color.rgb(18, 59, 10))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        addView(
            timeText,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16), Gravity.TOP).apply {
                leftMargin = dp(48)
                rightMargin = dp(48)
                topMargin = dp(145)
            }
        )

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(7), 0)
        }
        addView(
            controls,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42), Gravity.BOTTOM).apply {
                leftMargin = dp(5)
                rightMargin = dp(5)
                bottomMargin = dp(4)
            }
        )

        val previous = mediaButton(android.R.drawable.ic_media_previous, "Previous").apply {
            setOnClickListener {
                withControllerOrOpen { it.transportControls.skipToPrevious() }
            }
        }
        controls.addView(previous, LinearLayout.LayoutParams(dp(36), dp(34)).apply {
            marginEnd = dp(4)
        })

        playPause = mediaButton(android.R.drawable.ic_media_play, "Play or pause").apply {
            setOnClickListener {
                withControllerOrOpen { c ->
                    if (isPlaying()) c.transportControls.pause()
                    else c.transportControls.play()
                }
            }
        }
        controls.addView(playPause, LinearLayout.LayoutParams(dp(40), dp(38)).apply {
            marginEnd = dp(4)
        })

        val stop = tinyButton("STOP").apply {
            setOnClickListener { withControllerOrOpen { it.transportControls.stop() } }
        }
        controls.addView(stop, LinearLayout.LayoutParams(dp(43), dp(30)).apply {
            marginEnd = dp(4)
        })

        val next = mediaButton(android.R.drawable.ic_media_next, "Next").apply {
            setOnClickListener {
                withControllerOrOpen { it.transportControls.skipToNext() }
            }
        }
        controls.addView(next, LinearLayout.LayoutParams(dp(36), dp(34)).apply {
            marginEnd = dp(5)
        })

        shuffleButton = tinyButton("SHUF").apply {
            setOnClickListener { runMediaCustomAction("shuffle") }
        }
        controls.addView(shuffleButton, LinearLayout.LayoutParams(dp(43), dp(30)).apply {
            marginEnd = dp(4)
        })

        repeatButton = tinyButton("REP").apply {
            setOnClickListener { runMediaCustomAction("repeat") }
        }
        controls.addView(repeatButton, LinearLayout.LayoutParams(dp(42), dp(30)).apply {
            marginEnd = dp(5)
        })

        controls.addView(
            TextView(context).apply {
                text = "VOL"
                setTextColor(Color.rgb(20, 55, 10))
                textSize = 8f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.MATCH_PARENT)
        )

        volume = SeekBar(context).apply {
            max = 100
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    value: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser) return
                    setMediaVolume(value)
                }
            })
        }
        controls.addView(
            volume,
            LinearLayout.LayoutParams(0, dp(28), 1f)
        )

        albumArt.setOnClickListener { onOpenYouTubeMusic() }
        display.setOnClickListener {
            if (controller == null) {
                if (hasNotificationAccess()) onOpenYouTubeMusic()
                else onOpenNotificationAccess()
            }
        }

        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        post { restorePosition() }
        refreshController()
        mainHandler.removeCallbacks(ticker)
        mainHandler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        attached = false
        mainHandler.removeCallbacks(ticker)
        disconnectController()
        super.onDetachedFromWindow()
    }

    private fun refreshController() {
        val found = try {
            mediaSessionManager.getActiveSessions(listenerComponent)
                .firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (found?.sessionToken == controller?.sessionToken) return

        disconnectController()
        controller = found
        found?.registerCallback(controllerCallback, mainHandler)
        render()
    }

    private fun disconnectController() {
        try {
            controller?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {
        }
        controller = null
    }

    private fun render() {
        val c = controller
        val metadata = c?.metadata
        val state = c?.playbackState

        if (c == null) {
            albumArt.setImageResource(R.drawable.winsung_taskbar_ytmusic)
            albumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE
            albumArt.setPadding(dp(12), dp(12), dp(12), dp(12))
            titleText.text = "YouTube Music"
            artistText.text = if (hasNotificationAccess()) {
                "Open YTM to wake the player"
            } else {
                "Tap display: enable media access"
            }
            statusText.text = if (hasNotificationAccess()) {
                "MEDIA SESSION: STANDBY"
            } else {
                "MEDIA SESSION: ACCESS REQUIRED"
            }
            playPause.setImageResource(android.R.drawable.ic_media_play)
            spectrum.playing = false
            progress.progress = 0
            timeText.text = "00:00 / --:--"
            shuffleButton.alpha = 0.55f
            repeatButton.alpha = 0.55f
            renderVolume()
            return
        }

        albumArt.setPadding(0, 0, 0, 0)
        albumArt.scaleType = ImageView.ScaleType.CENTER_CROP
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art != null) {
            Glide.with(this).clear(albumArt)
            albumArt.setImageBitmap(art)
        } else {
            val artUri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            if (!artUri.isNullOrBlank()) {
                Glide.with(this)
                    .load(artUri)
                    .centerCrop()
                    .error(R.drawable.winsung_taskbar_ytmusic)
                    .into(albumArt)
            } else {
                albumArt.setImageResource(R.drawable.winsung_taskbar_ytmusic)
                albumArt.scaleType = ImageView.ScaleType.CENTER_INSIDE
                albumArt.setPadding(dp(12), dp(12), dp(12), dp(12))
            }
        }

        titleText.text = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Unknown track"
        artistText.text = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            ?: "YouTube Music"

        val playing = state?.state == PlaybackState.STATE_PLAYING
        statusText.text = when (state?.state) {
            PlaybackState.STATE_PLAYING -> "PLAYING // YTM LINK"
            PlaybackState.STATE_PAUSED -> "PAUSED // YTM LINK"
            PlaybackState.STATE_BUFFERING -> "BUFFERING // YTM LINK"
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SEEKING // YTM LINK"
            else -> "READY // YTM LINK"
        }
        playPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        spectrum.playing = playing

        shuffleButton.alpha = if (findCustomAction("shuffle") != null) 1f else 0.55f
        repeatButton.alpha = if (findCustomAction("repeat") != null) 1f else 0.55f
        repeatButton.text = "REP"

        renderPosition()
        renderVolume()
    }

    private fun renderPosition() {
        if (trackingSeek) return
        val state = controller?.playbackState ?: return
        val duration = mediaDuration()
        var position = state.position.coerceAtLeast(0L)

        if (
            state.state == PlaybackState.STATE_PLAYING &&
            state.lastPositionUpdateTime > 0L
        ) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }

        if (duration > 0L) {
            position = min(position, duration)
            progress.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
            timeText.text = "${formatTime(position)} / ${formatTime(duration)}"
        } else {
            progress.progress = 0
            timeText.text = "${formatTime(position)} / --:--"
        }
    }

    private fun renderTimePreview(value: Int) {
        val duration = mediaDuration()
        if (duration <= 0L) return
        val target = duration * value / 1000L
        timeText.text = "${formatTime(target)} / ${formatTime(duration)}"
    }

    private fun mediaDuration(): Long =
        controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            ?.coerceAtLeast(0L) ?: 0L

    private fun formatTime(ms: Long): String {
        val totalSeconds = max(0L, ms) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun isPlaying(): Boolean =
        controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun findCustomAction(keyword: String): PlaybackState.CustomAction? {
        return controller?.playbackState?.customActions
            ?.firstOrNull { action ->
                action.action.contains(keyword, ignoreCase = true) ||
                    action.name.toString().contains(keyword, ignoreCase = true)
            }
    }

    private fun runMediaCustomAction(keyword: String) {
        val c = controller
        if (c == null) {
            if (hasNotificationAccess()) onOpenYouTubeMusic()
            else onOpenNotificationAccess()
            return
        }

        val action = findCustomAction(keyword)
        if (action == null) {
            // YouTube Music does not consistently expose repeat/shuffle as platform
            // transport methods. If that session does not publish the custom action,
            // jump to YTM rather than pretending the button changed something.
            onOpenYouTubeMusic()
            return
        }

        c.transportControls.sendCustomAction(action.action, action.extras)
        mainHandler.postDelayed({ render() }, 150L)
    }

    private fun withControllerOrOpen(action: (MediaController) -> Unit) {
        val c = controller
        if (c == null) {
            if (hasNotificationAccess()) onOpenYouTubeMusic()
            else onOpenNotificationAccess()
            return
        }
        try {
            action(c)
        } catch (_: Exception) {
            onOpenYouTubeMusic()
        }
    }

    private fun renderVolume() {
        val info = controller?.playbackInfo
        if (info != null && info.maxVolume > 0) {
            volume.progress =
                ((info.currentVolume * 100f) / info.maxVolume).toInt().coerceIn(0, 100)
            return
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volume.progress = if (maxVolume > 0) {
            ((current * 100f) / maxVolume).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun setMediaVolume(percent: Int) {
        val info = controller?.playbackInfo
        if (info != null && info.maxVolume > 0) {
            val target = ((percent / 100f) * info.maxVolume).toInt()
            controller?.setVolumeTo(target, 0)
            return
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent / 100f) * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        return flat.split(":")
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == context.packageName }
    }

    private fun mediaButton(iconRes: Int, description: String): ImageButton =
        ImageButton(context).apply {
            setImageResource(iconRes)
            contentDescription = description
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = controlBackground()
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

    private fun tinyButton(label: String): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(25, 54, 17))
            textSize = 8f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            background = controlBackground()
            isClickable = true
            isFocusable = true
        }

    private fun makeSpeakerColumn(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            repeat(3) {
                addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.rgb(74, 74, 70))
                            setStroke(dp(3), Color.rgb(194, 255, 76))
                        }
                    },
                    LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        topMargin = dp(1)
                        bottomMargin = dp(1)
                    }
                )
            }
        }

    private fun shellBackground() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.rgb(56, 112, 22),
            Color.rgb(150, 255, 30),
            Color.rgb(82, 181, 22),
            Color.rgb(150, 255, 30),
            Color.rgb(56, 112, 22)
        )
    ).apply {
        cornerRadius = dp(38).toFloat()
        setStroke(dp(2), Color.rgb(26, 61, 9))
    }

    private fun screenBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(4, 7, 22), Color.rgb(0, 24, 37))
    ).apply {
        cornerRadius = dp(12).toFloat()
        setStroke(dp(2), Color.rgb(76, 255, 75))
    }

    private fun controlBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(244, 244, 238),
            Color.rgb(164, 168, 157),
            Color.rgb(230, 230, 223)
        )
    ).apply {
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), Color.rgb(50, 72, 42))
    }

    private fun enableDragging(handle: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = x
                    startY = y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && abs(dx) + abs(dy) > dp(8)) dragging = true

                    if (dragging) {
                        val parentView = parent as? ViewGroup
                        val maxX = max(0f, (parentView?.width ?: 0) - width.toFloat())
                        val maxY = max(0f, (parentView?.height ?: 0) - height.toFloat())
                        x = (startX + dx).coerceIn(0f, maxX)
                        y = (startY + dy).coerceIn(dp(36).toFloat(), maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) savePosition()
                    true
                }

                else -> false
            }
        }
    }

    private fun restorePosition() {
        val parentView = parent as? ViewGroup ?: return
        if (parentView.width <= 0 || parentView.height <= 0) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val defaultX = ((parentView.width - width) / 2f).coerceAtLeast(0f)
        val defaultY = (parentView.height - height - dp(104)).toFloat()
            .coerceAtLeast(dp(80).toFloat())
        val savedX = prefs.getFloat(PREF_X, defaultX)
        val savedY = prefs.getFloat(PREF_Y, defaultY)

        x = savedX.coerceIn(0f, max(0f, parentView.width - width.toFloat()))
        y = savedY.coerceIn(
            dp(36).toFloat(),
            max(dp(36).toFloat(), parentView.height - height.toFloat())
        )
    }

    private fun savePosition() {
        context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PREF_X, x)
            .putFloat(PREF_Y, y)
            .apply()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private class SpectrumView(context: Context) : View(context) {
        var playing: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(92, 255, 72)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bars = 18
            val gap = width / (bars * 3f)
            val barWidth = max(1f, gap * 1.8f)
            val phase = SystemClock.uptimeMillis() / 145.0

            for (i in 0 until bars) {
                val normalized = if (playing) {
                    0.25 + 0.75 * ((sin(phase + i * 0.83) + 1.0) / 2.0)
                } else {
                    0.16 + (i % 4) * 0.04
                }
                val h = (height * normalized).toFloat()
                val left = i * (barWidth + gap)
                canvas.drawRect(left, height - h, left + barWidth, height.toFloat(), paint)
            }

            if (playing) postInvalidateOnAnimation()
        }
    }
}
