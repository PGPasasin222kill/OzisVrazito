package ro.meowdoku.screen

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object Puzzle {
    // Relative coordinates based on the supplied 945x2048 screenshot.
    // They may need calibration on different screen layouts.
    private const val X0 = 0.026f
    private const val X1 = 0.974f
    private const val Y0 = 0.292f
    private const val Y1 = 0.683f

    data class Result(val columns: IntArray, val count: Int)
    fun bounds(w: Int, h: Int): FloatArray =
        floatArrayOf(w * X0, h * Y0, w * X1, h * Y1)

    fun detect(bitmap: Bitmap, n: Int = 10): Result? {
        if (n !in 4..12) return null
        val b = bounds(bitmap.width, bitmap.height)
        val palette = mutableListOf<Int>()
        val regions = IntArray(n*n)
        for (r in 0 until n) for (c in 0 until n) {
            val x = (b[0] + (c+0.5f) * (b[2]-b[0])/n).toInt().coerceIn(0,bitmap.width-1)
            val y = (b[1] + (r+0.5f) * (b[3]-b[1])/n).toInt().coerceIn(0,bitmap.height-1)
            val color = bitmap.getPixel(x,y)
            var nearest = -1
            var best = Int.MAX_VALUE
            for (i in palette.indices) {
                val d = distance(color,palette[i])
                if (d < best) { best = d; nearest = i }
            }
            if (nearest < 0 || best > 2100) {
                nearest = palette.size
                palette.add(color)
            }
            regions[r*n+c] = nearest
        }
        if (palette.size != n) return null
        val candidates = IntArray(n)
        val solution = IntArray(n) { -1 }
        var found = 0
        var first: IntArray? = null
        fun rec(r: Int, usedCols: Int, usedRegions: Int) {
            if (found >= 2) return
            if (r == n) {
                found++
                if (first == null) first = solution.copyOf()
                return
            }
            for (c in 0 until n) {
                if (usedCols and (1 shl c) != 0) continue
                if (r > 0 && abs(solution[r-1]-c) <= 1) continue
                val region = regions[r*n+c]
                if (usedRegions and (1 shl region) != 0) continue
                solution[r] = c
                rec(r+1,usedCols or (1 shl c),usedRegions or (1 shl region))
            }
        }
        rec(0,0,0)
        return first?.let { Result(it,found) }
    }
    private fun distance(a:Int,b:Int):Int {
        val r=Color.red(a)-Color.red(b)
        val g=Color.green(a)-Color.green(b)
        val bl=Color.blue(a)-Color.blue(b)
        return r*r+g*g+bl*bl
    }
}
