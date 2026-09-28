package com.clapfinder.app.prefs

import android.content.Context

object Prefs {
    private const val NAME = "smart_printer"

    private fun sp(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)


    var Context.isPremium: Boolean
        get() = sp(this).getBoolean("premium", false)
        set(v) {
            sp(this).edit().putBoolean("premium", v).apply()
        }
}