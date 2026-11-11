package ro.meowdoku.screen

import android.content.Context

/** Persisted user settings. Does not disable Android's mandatory screen-capture consent. */
object UserPrefs {
    private const val FILE = "meowdoku_settings"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** AUTO is off until the user explicitly enables it in CatPilot settings. */
    fun autoSolver(ctx: Context) = prefs(ctx).getBoolean("auto_solver_enabled", false)
    fun setAutoSolver(ctx: Context, value:Boolean) =
        prefs(ctx).edit().putBoolean("auto_solver_enabled", value).apply()

    /** Continue only after AUTO has just solved a board. Enabled by user AUTO mode. */
    fun autoContinue(ctx:Context)=prefs(ctx).getBoolean("auto_continue",true)
    fun setAutoContinue(ctx:Context,enabled:Boolean)=
        prefs(ctx).edit().putBoolean("auto_continue",enabled).apply()

    /** Extra wait after a verified result screen is dismissed (milliseconds). */
    fun continueDelayMs(ctx:Context)=
        prefs(ctx).getInt("continue_delay_ms",1100).coerceIn(700,2400)
    fun setContinueDelayMs(ctx:Context,value:Int)=
        prefs(ctx).edit().putInt("continue_delay_ms",value.coerceIn(700,2400)).apply()

    fun autoRetryLimit(ctx:Context):Int {
        val value=prefs(ctx).getInt("auto_retry_limit",5)
        return if(value in listOf(3,5,8)) value else 5
    }
    fun setAutoRetryLimit(ctx:Context,value:Int)=
        prefs(ctx).edit().putInt("auto_retry_limit",value.coerceIn(3,8)).apply()

    /** Always avoid blind fallback taps if no supported result overlay is detected. */
    fun safeAuto(ctx:Context)=prefs(ctx).getBoolean("safe_auto",true)
    fun setSafeAuto(ctx:Context,enabled:Boolean)=
        prefs(ctx).edit().putBoolean("safe_auto",enabled).apply()

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

    fun autoHideLevelChange(ctx: Context) = prefs(ctx).getBoolean("auto_hide_on_level_change",true)
    fun setAutoHideLevelChange(ctx: Context, value:Boolean) =
        prefs(ctx).edit().putBoolean("auto_hide_on_level_change",value).apply()

    /** 0=star, 1=dot, 2=ring, 3=cat */
    fun markerShape(ctx: Context) = prefs(ctx).getInt("marker_shape",0).coerceIn(0,3)
    fun setMarkerShape(ctx: Context, value:Int) =
        prefs(ctx).edit().putInt("marker_shape",value.coerceIn(0,3)).apply()

    /** Color in RGB, stored as an Android Color int. */
    fun markerColor(ctx: Context) = prefs(ctx).getInt("marker_color",android.graphics.Color.YELLOW)
    fun setMarkerColor(ctx: Context, value:Int) =
        prefs(ctx).edit().putInt("marker_color",value).apply()

    fun saveBoard(ctx: Context, board:Puzzle.Board, width:Int, height:Int) {
        prefs(ctx).edit()
            .putBoolean("manual_calibration",true)
            .putFloat("manual_x0",board.left.toFloat()/width)
            .putFloat("manual_y0",board.top.toFloat()/height)
            .putFloat("manual_x1",board.right.toFloat()/width)
            .putFloat("manual_y1",board.bottom.toFloat()/height)
            .apply()
    }
    fun manualBoard(ctx: Context, width:Int, height:Int): Puzzle.Board? {
        if(!prefs(ctx).getBoolean("manual_calibration",false))return null
        val values=floatArrayOf(
            prefs(ctx).getFloat("manual_x0",.03f),
            prefs(ctx).getFloat("manual_y0",.33f),
            prefs(ctx).getFloat("manual_x1",.97f),
            prefs(ctx).getFloat("manual_y1",.77f)
        )
        val b=Puzzle.Board(
            (values[0]*width).toInt().coerceIn(0,width-1),
            (values[1]*height).toInt().coerceIn(0,height-1),
            (values[2]*width).toInt().coerceIn(0,width-1),
            (values[3]*height).toInt().coerceIn(0,height-1)
        )
        return if(b.right-b.left<100 || b.bottom-b.top<100) null else b
    }
    fun clearManualBoard(ctx: Context) =
        prefs(ctx).edit().putBoolean("manual_calibration",false).apply()
    fun hasManualBoard(ctx:Context)=prefs(ctx).getBoolean("manual_calibration",false)

    fun saveDiagnosis(ctx:Context, message:String, grid:Int, colors:Int) =
        prefs(ctx).edit()
            .putString("last_diagnosis",message)
            .putInt("last_grid",grid)
            .putInt("last_colors",colors).apply()
    fun diagnosis(ctx:Context):String {
        val p=prefs(ctx)
        val size=p.getInt("last_grid",0)
        val colors=p.getInt("last_colors",0)
        val message=p.getString("last_diagnosis","Nu ai scanat încă o tablă.") ?: ""
        return (if(size>0) "Grilă: "+size+"×"+size+" | Culori: "+colors+"\\n" else "")+message
    }
}
