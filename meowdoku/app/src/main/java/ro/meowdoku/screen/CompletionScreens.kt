package ro.meowdoku.screen

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Conservative, screenshot-only recognizer for Meowdoku's two result overlays.
 * No text/OCR, arbitrary taps, Accessibility node access or network access.
 *
 * Calibrated on screenshots provided by the player. If a theme or device layout
 * differs, report UNKNOWN instead of tapping a guess.
 */
object CompletionScreens {
    enum class Kind { LEADERBOARD, FLAWLESS, UNKNOWN }

    private fun ratio(
        bitmap:Bitmap, x0:Float, y0:Float, x1:Float, y1:Float,
        predicate:(Int,Int,Int)->Boolean
    ):Float {
        val left=(bitmap.width*x0).toInt().coerceIn(0,bitmap.width-1)
        val right=(bitmap.width*x1).toInt().coerceIn(left+1,bitmap.width)
        val top=(bitmap.height*y0).toInt().coerceIn(0,bitmap.height-1)
        val bottom=(bitmap.height*y1).toInt().coerceIn(top+1,bitmap.height)
        var matching=0
        var total=0
        val stepX=((right-left)/100).coerceAtLeast(3)
        val stepY=((bottom-top)/70).coerceAtLeast(3)
        for(y in top until bottom step stepY) {
            for(x in left until right step stepX) {
                val pixel=bitmap.getPixel(x,y)
                if(predicate(Color.red(pixel),Color.green(pixel),Color.blue(pixel)))matching++
                total++
            }
        }
        return matching.toFloat()/total.coerceAtLeast(1)
    }

    fun classify(bitmap:Bitmap):Kind {
        if(bitmap.width<280 || bitmap.height<440)return Kind.UNKNOWN
        val topDark=ratio(bitmap,.18f,.04f,.82f,.105f) { r,g,b ->
            maxOf(r,g,b)<78
        }
        if(topDark<.72f)return Kind.UNKNOWN

        // Large orange "next level" pill across the lower middle of Flawless.
        val orangePill=ratio(bitmap,.18f,.72f,.82f,.82f) { r,g,b ->
            r>195 && g in 70..204 && b<130 && r>g*1.22f
        }
        val paleCards=ratio(bitmap,.18f,.50f,.82f,.78f) { r,g,b ->
            r>155 && g>145 && b>130
        }
        if(orangePill>.34f && paleCards<.24f) return Kind.FLAWLESS

        // Leaderboard has multiple broad cream-white list rows beneath podium.
        val lowerCards=ratio(bitmap,.18f,.53f,.82f,.77f) { r,g,b ->
            r>155 && g>145 && b>130
        }
        val tapToContinue=ratio(bitmap,.18f,.84f,.82f,.90f) { r,g,b ->
            r>180 && g>140 && b<150
        }
        if(lowerCards>.48f && orangePill<.20f && tapToContinue>.014f)
            return Kind.LEADERBOARD

        return Kind.UNKNOWN
    }

    /** Tap areas are fractions, so they scale to the actual screenshot size. */
    fun tapPoint(kind:Kind):Pair<Float,Float>? = when(kind) {
        Kind.FLAWLESS -> .50f to .78f
        Kind.LEADERBOARD -> .50f to .87f
        Kind.UNKNOWN -> null
    }
}
