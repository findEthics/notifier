package com.example.notifier.Calendar

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CalendarEvent(
    val summary: String,
    val startTime: String
) : Parcelable
