package me.rerere.rikkahub.utils

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics

internal fun createAppAnalytics(): AppAnalytics {
    Firebase.crashlytics
    val analytics = Firebase.analytics
    return AppAnalytics { name ->
        analytics.logEvent(name, null)
    }
}
