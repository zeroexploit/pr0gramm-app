package com.pr0gramm.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.Surface


object Screen {
    private const val LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    private const val PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private const val REVERSE_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    private const val REVERSE_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    private const val UNSPECIFIED = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED


    fun lockOrientation(activity: Activity) {
        val display = activity.window.decorView.display
        val rotation = display.rotation

        val size = activity.resources.configuration
        val width = size.screenWidthDp
        val height = size.screenHeightDp

        when (rotation) {
            Surface.ROTATION_90 -> if (width > height) {
                activity.requestedOrientation = LANDSCAPE
            } else {
                activity.requestedOrientation = REVERSE_PORTRAIT
            }

            Surface.ROTATION_180 -> if (height > width) {
                activity.requestedOrientation = REVERSE_PORTRAIT
            } else {
                activity.requestedOrientation = REVERSE_LANDSCAPE
            }

            Surface.ROTATION_270 -> if (width > height) {
                activity.requestedOrientation = REVERSE_LANDSCAPE
            } else {
                activity.requestedOrientation = PORTRAIT
            }

            else -> if (height > width) {
                activity.requestedOrientation = PORTRAIT
            } else {
                activity.requestedOrientation = LANDSCAPE
            }
        }
    }


    fun unlockOrientation(activity: Activity) {
        activity.requestedOrientation = UNSPECIFIED
    }
}
