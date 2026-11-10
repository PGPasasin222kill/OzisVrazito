package ro.meowdoku.screen

import android.content.Context

/** Persisted user settings. Does not disable Android's mandatory screen-capture consent. */
object UserPrefs {
    private const val FILE = "meowdoku_settings"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun showMessages(ctx: Context) = prefs(ctx).getBoolean("show_messages", false)
    fun setShowMessages(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean("show_messages", value).apply()

    fun sizeDp(ctx: Context) = prefs(ctx).getInt("floating_button_size_dp", 42).coerceIn(24, 64)
    fun setSizeDp(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt("floating_button_size_dp", value.coerceIn(24, 64)).apply()

    fun starOpacity(ctx: Context) = prefs(ctx).getInt("star_opacity", 65).coerceIn(20, 100)
    fun setStarOpacity(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt("star_opacity", value.coerceIn(20, 100)).apply()

    /** Duration in seconds; 0 = until user hides the markers. */
    fun duration(ctx: Context): Int {
        val saved=prefs(ctx).getInt("star_duration_seconds",20)
        return if (saved in listOf(0,3,5,10,20)) saved else 20
    }
    fun setDuration(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt("star_duration_seconds", if (value in listOf(0,3,5,10,20)) value else 20).apply()

    fun verifyResult(ctx: Context) = prefs(ctx).getBoolean("verify_result",true)
    fun setVerifyResult(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean("verify_result",value).apply()

    // Position in display pixels, independent of the current overlay width.
    fun overlayX(ctx: Context): Int = prefs(ctx).getInt("overlay_x",-1)
    fun overlayY(ctx: Context): Int = prefs(ctx).getInt("overlay_y",-1)
    fun saveOverlayPosition(ctx: Context, x: Int, y: Int) =
        prefs(ctx).edit().putInt("overlay_x",x).putInt("overlay_y",y).apply()
}
