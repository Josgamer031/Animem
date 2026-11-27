package com.example.ui.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PipController(private val activity: Activity) {

    private val _isInPictureInPictureMode = MutableStateFlow(false)
    val isInPictureInPictureMode: StateFlow<Boolean> = _isInPictureInPictureMode.asStateFlow()

    val isPipSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    @Volatile
    private var canEnterPip: Boolean = false

    @Volatile
    private var isVideoPlaying: Boolean = false

    fun setPlayerPipEligibility(canEnter: Boolean, isPlaying: Boolean) {
        canEnterPip = canEnter
        isVideoPlaying = isPlaying
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPipSupported) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(canEnter && isPlaying)
                    .build()
                activity.setPictureInPictureParams(params)
            }
        }
    }

    fun enterPictureInPicture(): Boolean {
        if (!isPipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(canEnterPip && isVideoPlaying)
            }
            activity.enterPictureInPictureMode(builder.build())
        }.getOrDefault(false)
    }

    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S &&
            isPipSupported &&
            canEnterPip &&
            isVideoPlaying
        ) {
            enterPictureInPicture()
        }
    }

    fun onPictureInPictureModeChanged(isInPip: Boolean) {
        _isInPictureInPictureMode.value = isInPip
    }
}

val LocalPipController = staticCompositionLocalOf<PipController?> { null }
