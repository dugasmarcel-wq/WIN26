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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
 * Compact desktop controller for YouTube Music's real Android MediaSession.
 *
 * Playback stays in YouTube Music. WIN26 only presents the controls/metadata that the
 * operating system exposes, so there is no separate streaming or background network path.
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
        private const val MOVE_HOLD_MS = 430L
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
    private val moveStatus: TextView

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
        background = outerShell()
        clipToPadding = false
        elevation = dp(6).toFloat()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, dp(5), 0)
            background = headerBackground()
        }

        val brand = TextView(context).apply {
            text = "WINSUNG MEDIA DECK 98"
            setTextColor(Color.rgb(220, 230, 236))
            textSize = 8.8f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.05f
            maxLines = 1
        }
        header.addView(
            brand,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        )

        moveStatus = TextView(context).apply {
            text = "HOLD TO MOVE"
            setTextColor(Color.rgb(135, 157, 171))
            textSize = 7.2f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            maxLines = 1
        }
        header.addView(
            moveStatus,
            LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val ytmButton = tinyButton("YTM").apply {
            setOnClickListener { onOpenYouTubeMusic() }
        }
        header.addView(
            ytmButton,
            LinearLayout.LayoutParams(dp(36), dp(19)).apply {
                marginStart = dp(4)
            }
        )

        addView(
            header,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24), Gravity.TOP).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                topMargin = dp(4)
            }
        )
        enableLongPressDragging(header)

        val display = FrameLayout(context).apply {
            background = displayBackground()
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        addView(
            display,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(69), Gravity.TOP).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                topMargin = dp(31)
            }
        )

        albumArt = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = albumBackground()
            clipToOutline = true
        }
        display.addView(
            albumArt,
            LayoutParams(dp(55), dp(55), Gravity.START or Gravity.TOP)
        )

        val infoColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(2), 0)
        }
        display.addView(
            infoColumn,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55), Gravity.TOP).apply {
                leftMargin = dp(58)
            }
        )

        titleText = TextView(context).apply {
            text = "YouTube Music"
            setTextColor(Color.rgb(224, 235, 241))
            textSize = 11.8f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoColumn.addView(titleText)

        artistText = TextView(context).apply {
            text = "Open YTM to start"
            setTextColor(Color.rgb(118, 186, 207))
            textSize = 9.3f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        infoColumn.addView(artistText)

        statusText = TextView(context).apply {
            text = "SESSION STANDBY"
            setTextColor(Color.rgb(125, 143, 153))
            textSize = 7.4f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            setPadding(0, dp(3), 0, 0)
        }
        infoColumn.addView(statusText)

        spectrum = SpectrumView(context)
        display.addView(
            spectrum,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10), Gravity.BOTTOM).apply {
                leftMargin = dp(61)
                rightMargin = dp(2)
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
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18), Gravity.TOP).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                topMargin = dp(101)
            }
        )

        timeText = TextView(context).apply {
            text = "00:00 / --:--"
            setTextColor(Color.rgb(54, 67, 76))
            textSize = 7.8f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        addView(
            timeText,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12), Gravity.TOP).apply {
                topMargin = dp(117)
            }
        )

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), 0, dp(7), 0)
            background = lowerRailBackground()
        }
        addView(
            controls,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32), Gravity.BOTTOM).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(4)
            }
        )

        val previous = mediaButton(android.R.drawable.ic_media_previous, "Previous").apply {
            setOnClickListener {
                withControllerOrOpen { it.transportControls.skipToPrevious() }
            }
        }
        controls.addView(previous, LinearLayout.LayoutParams(dp(29), dp(27)).apply {
            marginEnd = dp(3)
        })

        playPause = mediaButton(android.R.drawable.ic_media_play, "Play or pause").apply {
            setOnClickListener {
                withControllerOrOpen { c ->
                    if (isPlaying()) c.transportControls.pause()
                    else c.transportControls.play()
                }
            }
        }
        controls.addView(playPause, LinearLayout.LayoutParams(dp(32), dp(29)).apply {
            marginEnd = dp(3)
        })

        val stop = tinyButton("STOP").apply {
            setOnClickListener {
                withControllerOrOpen { it.transportControls.stop() }
            }
        }
        controls.addView(stop, LinearLayout.LayoutParams(dp(33), dp(24)).apply {
            marginEnd = dp(3)
        })

        val next = mediaButton(android.R.drawable.ic_media_next, "Next").apply {
            setOnClickListener {
                withControllerOrOpen { it.transportControls.skipToNext() }
            }
        }
        controls.addView(next, LinearLayout.LayoutParams(dp(29), dp(27)).apply {
            marginEnd = dp(4)
        })

        shuffleButton = tinyButton("SHF").apply {
            setOnClickListener { runMediaCustomAction("shuffle") }
        }
        controls.addView(shuffleButton, LinearLayout.LayoutParams(dp(31), dp(24)).apply {
            marginEnd = dp(3)
        })

        repeatButton = tinyButton("REP").apply {
            setOnClickListener { runMediaCustomAction("repeat") }
        }
        controls.addView(repeatButton, LinearLayout.LayoutParams(dp(31), dp(24)).apply {
            marginEnd = dp(4)
        })

        controls.addView(
            TextView(context).apply {
                text = "VOL"
                setTextColor(Color.rgb(66, 78, 87))
                textSize = 7f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.MATCH_PARENT)
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
                    if (fromUser) setMediaVolume(value)
                }
            })
        }
        controls.addView(volume, LinearLayout.LayoutParams(0, dp(24), 1f))

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
            albumArt.setPadding(dp(9), dp(9), dp(9), dp(9))
            titleText.text = "YouTube Music"
            artistText.text = if (hasNotificationAccess()) {
                "Open YTM to wake the player"
            } else {
                "Tap display to enable media access"
            }
            statusText.text = if (hasNotificationAccess()) {
                "SESSION STANDBY"
            } else {
                "MEDIA ACCESS REQUIRED"
            }
            playPause.setImageResource(android.R.drawable.ic_media_play)
            spectrum.playing = false
            progress.progress = 0
            timeText.text = "00:00 / --:--"
            shuffleButton.alpha = 0.45f
            repeatButton.alpha = 0.45f
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
                albumArt.setPadding(dp(9), dp(9), dp(9), dp(9))
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
            PlaybackState.STATE_PLAYING -> "PLAYING  //  YTM SESSION"
            PlaybackState.STATE_PAUSED -> "PAUSED  //  YTM SESSION"
            PlaybackState.STATE_BUFFERING -> "BUFFERING  //  YTM SESSION"
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SEEKING  //  YTM SESSION"
            else -> "READY  //  YTM SESSION"
        }
        playPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        spectrum.playing = playing

        shuffleButton.alpha = if (findCustomAction("shuffle") != null) 1f else 0.45f
        repeatButton.alpha = if (findCustomAction("repeat") != null) 1f else 0.45f
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
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = controlBackground()
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

    private fun tinyButton(label: String): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(51, 63, 71))
            textSize = 7.4f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            background = controlBackground()
            isClickable = true
            isFocusable = true
        }

    private fun outerShell() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(104, 124, 138),
            Color.rgb(190, 200, 205),
            Color.rgb(121, 139, 150)
        )
    ).apply {
        cornerRadius = dp(15).toFloat()
        setStroke(dp(2), Color.rgb(48, 61, 70))
    }

    private fun headerBackground() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(
            Color.rgb(27, 42, 54),
            Color.rgb(48, 73, 91),
            Color.rgb(27, 42, 54)
        )
    ).apply {
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.rgb(98, 121, 136))
    }

    private fun displayBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(6, 13, 19),
            Color.rgb(11, 28, 38),
            Color.rgb(7, 17, 24)
        )
    ).apply {
        cornerRadius = dp(8).toFloat()
        setStroke(dp(1), Color.rgb(88, 138, 157))
    }

    private fun albumBackground() = GradientDrawable().apply {
        setColor(Color.rgb(18, 29, 37))
        setStroke(dp(1), Color.rgb(87, 118, 132))
        cornerRadius = dp(5).toFloat()
    }

    private fun lowerRailBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(203, 210, 214),
            Color.rgb(154, 167, 174)
        )
    ).apply {
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.rgb(78, 92, 101))
    }

    private fun controlBackground() = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            Color.rgb(242, 244, 245),
            Color.rgb(187, 196, 201),
            Color.rgb(225, 229, 231)
        )
    ).apply {
        cornerRadius = dp(11).toFloat()
        setStroke(dp(1), Color.rgb(76, 88, 96))
    }

    private fun enableLongPressDragging(handle: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var moveArmed = false
        var gestureCancelled = false

        val armMove = Runnable {
            if (!gestureCancelled) {
                moveArmed = true
                moveStatus.text = "MOVE MODE"
                moveStatus.setTextColor(Color.rgb(236, 196, 96))
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = x
                    startY = y
                    moveArmed = false
                    gestureCancelled = false
                    mainHandler.removeCallbacks(armMove)
                    mainHandler.postDelayed(armMove, MOVE_HOLD_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (!moveArmed && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        gestureCancelled = true
                        mainHandler.removeCallbacks(armMove)
                    }

                    if (moveArmed) {
                        val parentView = parent as? ViewGroup
                        val maxX = max(0f, (parentView?.width ?: 0) - width.toFloat())
                        val maxY = max(0f, (parentView?.height ?: 0) - height.toFloat())
                        x = (startX + dx).coerceIn(0f, maxX)
                        y = (startY + dy).coerceIn(dp(34).toFloat(), maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(armMove)
                    if (moveArmed) savePosition()
                    moveArmed = false
                    gestureCancelled = false
                    moveStatus.text = "HOLD TO MOVE"
                    moveStatus.setTextColor(Color.rgb(135, 157, 171))
                    true
                }

                else -> true
            }
        }
    }

    private fun restorePosition() {
        val parentView = parent as? ViewGroup ?: return
        if (parentView.width <= 0 || parentView.height <= 0) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val defaultX = ((parentView.width - width) / 2f).coerceAtLeast(0f)
        val defaultY = (parentView.height - height - dp(92)).toFloat()
            .coerceAtLeast(dp(70).toFloat())
        val savedX = prefs.getFloat(PREF_X, defaultX)
        val savedY = prefs.getFloat(PREF_Y, defaultY)

        x = savedX.coerceIn(0f, max(0f, parentView.width - width.toFloat()))
        y = savedY.coerceIn(
            dp(34).toFloat(),
            max(dp(34).toFloat(), parentView.height - height.toFloat())
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
            color = Color.rgb(91, 179, 205)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bars = 20
            val gap = width / (bars * 3.1f)
            val barWidth = max(1f, gap * 1.7f)
            val phase = SystemClock.uptimeMillis() / 160.0

            for (i in 0 until bars) {
                val normalized = if (playing) {
                    0.22 + 0.78 * ((sin(phase + i * 0.77) + 1.0) / 2.0)
                } else {
                    0.16 + (i % 5) * 0.025
                }
                val h = (height * normalized).toFloat()
                val left = i * (barWidth + gap)
                canvas.drawRect(left, height - h, left + barWidth, height.toFloat(), paint)
            }

            if (playing) postInvalidateOnAnimation()
        }
    }
}
