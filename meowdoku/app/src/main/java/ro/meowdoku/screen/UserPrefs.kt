package ro.meowdoku.screen

import android.content.Context

/** Settings kept across application and floating overlay restarts. */
object UserPrefs {
    private const val FILE = "meowdoku_settings"
    private const val SHOW_MESSAGES = "show_messages"
    private const val SIZE_DP = "floating_button_size_dp"

    fun showMessages(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(SHOW_MESSAGES, false)

    fun setShowMessages(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(SHOW_MESSAGES, enabled).apply()
    }

    fun sizeDp(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(SIZE_DP, 42).coerceIn(36, 64)

    fun setSizeDp(context: Context, size: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(SIZE_DP, size.coerceIn(36, 64)).apply()
    }
}
