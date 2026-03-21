package com.example.languagereminder.util

import java.time.DayOfWeek
import java.time.LocalDate

object DayOfWeekResolver {
    fun today(): DayOfWeek = LocalDate.now().dayOfWeek
}
