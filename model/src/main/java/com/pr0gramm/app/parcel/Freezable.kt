package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat

inline fun <reified T : Parcelable> Bundle.getParcelableOrNull(key: String): T? {
    return BundleCompat.getParcelable(this, key, T::class.java)
}

inline fun <reified T : Parcelable?> Bundle.getParcelableOrThrow(key: String): T {
    val value = BundleCompat.getParcelable(this, key, Parcelable::class.java)

    require(value is T) {
        if (value == null) {
            "No parcelable found for '$key'"
        } else {
            "Parcelable '$key' not of expected type"
        }
    }

    return value
}

inline fun <reified T : Parcelable> Intent.getExtraParcelableOrNull(key: String): T? {
    return IntentCompat.getParcelableExtra(this, key, T::class.java)
}

inline fun <reified T : Parcelable> Intent.getExtraParcelableOrThrow(key: String): T {
    val extras = this.extras ?: throw IllegalArgumentException("no extras set on intent")
    return extras.getParcelableOrThrow<T>(key)
}
