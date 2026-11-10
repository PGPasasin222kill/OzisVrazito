package ro.meowdoku.screen

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Detects the actual colored square board and counts its grid lines.
 *  No hardcoded board position and no fixed 8x8 / 10x10 sizes.
 *  Color is sampled near cell edges, away from X symbols and cat icons.
 */
object Puzzle {
    data class Board(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun asFloats() = floatArrayOf(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }
    data class Result(val columns: IntArray, val count: Int, val board: Board) {
        val size: Int get() = columns.size
    }
    data class Scan(val result: Result?, val message: String)

    private data class Span(val start: Int, val end: Int) {
        val length get() = end - start + 1
    }
    private data class RegionColor(var color: Int, var count: Int)
    private val samplePositions = arrayOf(
        .18f to .18f, .82f to .18f, .18f to .82f, .82f to .82f,
        .16f to .50f, .84f to .50f, .50f to .16f, .50f to .84f
    )

    fun detect(bitmap: Bitmap, strict: Boolean = true): Scan {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 200 || h < 250) return Scan(null, "Captură prea mică.")
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        fun colorful(x: Int, y: Int): Boolean {
            val rgb = pixels[y * w + x]
            val red = Color.red(rgb)
            val green = Color.green(rgb)
            val blue = Color.blue(rgb)
            return maxOf(red, green, blue) - minOf(red, green, blue) > 38
        }

        // First locate the large, densely colored square, not the header or palette.
        val rowActivity = DoubleArray(h) { y ->
            var hits = 0
            var total = 0
            for (x in (w * .08).toInt() until (w * .92).toInt() step 4) {
                if (colorful(x, y)) hits++
                total++
            }
            hits.toDouble() / total.coerceAtLeast(1)
        }
        val rows = longestBand(rowActivity, 12, .32, (w * .48).toInt())
            ?: return Scan(null, "Nu am găsit tabla colorată pe ecran.")
        fun edgeStart(activity: DoubleArray, p: Int): Int {
            for (i in (p - 9).coerceAtLeast(0)..(p + 10).coerceAtMost(activity.lastIndex))
                if (activity[i] > .30) return i
            return p
        }
        fun edgeEnd(activity: DoubleArray, p: Int): Int {
            for (i in (p + 9).coerceAtMost(activity.lastIndex) downTo (p - 10).coerceAtLeast(0))
                if (activity[i] > .30) return i
            return p
        }
        val y0 = edgeStart(rowActivity, rows.start)
        val y1 = edgeEnd(rowActivity, rows.end)
        val colActivity = DoubleArray(w) { x ->
            var hits = 0
            var total = 0
            for (y in y0..y1 step 4) {
                if (colorful(x, y)) hits++
                total++
            }
            hits.toDouble() / total.coerceAtLeast(1)
        }
        val cols = longestBand(colActivity, 12, .32, (w * .4).toInt())
            ?: return Scan(null, "Nu pot localiza marginile tablei.")
        val x0 = edgeStart(colActivity, cols.start)
        val x1 = edgeEnd(colActivity, cols.end)
        if (x1 <= x0 || y1 <= y0 ||
            abs((x1-x0).toDouble()/(y1-y0) - 1.0) > .18)
            return Scan(null, "Tabla găsită nu este pătrată.")

        // The white gutters between adjacent cells identify the exact grid size.
        val colGutterActivity = DoubleArray(w) { x ->
            var hits=0
            var total=0
            for (y in (y0+5)..(y1-5) step 3) {
                if (colorful(x,y)) hits++
                total++
            }
            hits.toDouble()/total.coerceAtLeast(1)
        }
        val rowGutterActivity = DoubleArray(h) { y ->
            var hits=0
            var total=0
            for (x in (x0+5)..(x1-5) step 3) {
                if (colorful(x,y)) hits++
                total++
            }
            hits.toDouble()/total.coerceAtLeast(1)
        }
        val xGaps = gutters(colGutterActivity, x0, x1)
        val yGaps = gutters(rowGutterActivity, y0, y1)
        val n = xGaps.size + 1
        if (n < 3 || n > 24 || yGaps.size + 1 != n ||
            !regular(xGaps) || !regular(yGaps))
            return Scan(null, "Nu am identificat corect numărul rândurilor și coloanelor.")

        val board = Board(x0, y0, x1, y1)
        val raw = arrayOfNulls<Int>(n*n)
        val bw = (x1-x0+1).toFloat()
        val bh = (y1-y0+1).toFloat()
        for (r in 0 until n) for (c in 0 until n) {
            val samples = ArrayList<Int>(samplePositions.size)
            for ((fx, fy) in samplePositions) {
                val x = (x0+(c+fx)*bw/n).toInt().coerceIn(0,w-1)
                val y = (y0+(r+fy)*bh/n).toInt().coerceIn(0,h-1)
                if (colorful(x,y)) samples.add(pixels[y*w+x])
            }
            var best = 0
            var center = 0
            for (candidate in samples) {
                val support = samples.count { distance(it,candidate) < 900 }
                if (support > best) { best = support; center = candidate }
            }
            if (best >= 2) {
                val consistent = samples.filter { distance(it,center) < 900 }
                val red = consistent.sumOf { Color.red(it) } / consistent.size
                val green = consistent.sumOf { Color.green(it) } / consistent.size
                val blue = consistent.sumOf { Color.blue(it) } / consistent.size
                raw[r*n+c] = Color.rgb(red, green, blue)
            }
        }

        // Merge similar tile colors; X marks / cats are ignored by edge sampling.
        val palette = ArrayList<RegionColor>()
        val assignments = IntArray(n*n) { -1 }
        for (i in raw.indices) {
            val color = raw[i] ?: continue
            var bestIndex = -1
            var bestDistance = Int.MAX_VALUE
            for (p in palette.indices) {
                val d = distance(color, palette[p].color)
                if (d < bestDistance) { bestDistance = d; bestIndex = p }
            }
            if (bestIndex < 0 || bestDistance > 900) {
                assignments[i] = palette.size
                palette.add(RegionColor(color,1))
            } else {
                assignments[i] = bestIndex
                val old=palette[bestIndex]
                val k=old.count
                old.color=Color.rgb(
                    (Color.red(old.color)*k+Color.red(color))/(k+1),
                    (Color.green(old.color)*k+Color.green(color))/(k+1),
                    (Color.blue(old.color)*k+Color.blue(color))/(k+1)
                )
                old.count++
            }
        }
        if (palette.size < n)
            return Scan(null, "Am detectat "+palette.size+" culori pentru o tablă "+n+"×"+n+".")

        // Discard isolated abnormal colors caused by an overlapping window.
        val chosen = palette.indices.sortedWith(
            compareByDescending<Int> { palette[it].count }
                .thenByDescending { saturation(palette[it].color) }
        ).take(n)
        val regions = IntArray(n*n) { -1 }
        for (i in raw.indices) {
            val color = raw[i] ?: continue
            var best = Int.MAX_VALUE
            var index = -1
            for (p in chosen.indices) {
                val d=distance(color,palette[chosen[p]].color)
                if (d<best) { best=d; index=p }
            }
            if (best < 1500) regions[i] = index
        }
        // Optional confidence gate: reject screenshots with too many uncertain cells.
        val unknownBeforeFill=regions.count { it<0 }
        if(strict && unknownBeforeFill>maxOf(2,n*n/15))
            return Scan(null,"Prea multe căsuțe sunt acoperite. Mută butonul MEOW și încearcă din nou.")
        // If a portion of a colored cell was covered, infer from its neighbors.
        repeat(n) {
            for (i in regions.indices) {
                if (regions[i] >= 0) continue
                val r=i/n
                val c=i%n
                val adj=ArrayList<Int>()
                if (r>0 && regions[i-n]>=0) adj.add(regions[i-n])
                if (r+1<n && regions[i+n]>=0) adj.add(regions[i+n])
                if (c>0 && regions[i-1]>=0) adj.add(regions[i-1])
                if (c+1<n && regions[i+1]>=0) adj.add(regions[i+1])
                if (adj.isNotEmpty()) regions[i]=adj.groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }!!.key
            }
        }
        if (regions.any { it<0 } || regions.toSet().size != n)
            return Scan(null, "Unele regiuni sunt acoperite sau culorile nu sunt clare.")
        if(strict && !regionsConnected(regions,n))
            return Scan(null, "Verificarea a găsit regiuni neclare. Reîncearcă pe tabla complet vizibilă.")

        val sol = IntArray(n) { -1 }
        var found=0
        var first: IntArray?=null
        var operations=0
        var timedOut=false
        val deadline=SystemClock.elapsedRealtime()+2500
        fun search(r: Int, usedColumns: Long, usedRegions: Long) {
            if (found >= 2 || timedOut) return
            operations++
            if (operations % 1024 == 0 && SystemClock.elapsedRealtime()>deadline) {
                timedOut=true
                return
            }
            if (r==n) {
                found++
                if (first==null) first=sol.copyOf()
                return
            }
            for (c in 0 until n) {
                if (usedColumns and (1L shl c) != 0L) continue
                if (r>0 && abs(sol[r-1]-c)<=1) continue
                val region=regions[r*n+c]
                if (usedRegions and (1L shl region) != 0L) continue
                sol[r]=c
                search(r+1, usedColumns or (1L shl c), usedRegions or (1L shl region))
            }
        }
        search(0,0L,0L)
        if (timedOut) return Scan(null, "Nivelul este prea complex pentru scanarea rapidă.")
        val solution=first ?: return Scan(null,"Nu găsesc o soluție: verifică dacă tabla e complet vizibilă.")
        if (found!=1) return Scan(null,"Am găsit mai multe soluții: o regiune poate fi citită greșit.")
        return Scan(Result(solution,found,board),"Soluție "+n+"×"+n)
    }

    /** Each colored zone of the original puzzle should form one contiguous region. */
    private fun regionsConnected(regions:IntArray, n:Int):Boolean {
        for (region in 0 until n) {
            val first=regions.indexOfFirst { it==region }
            if(first<0)return false
            val queue=IntArray(n*n)
            val seen=BooleanArray(n*n)
            var front=0
            var end=1
            queue[0]=first
            seen[first]=true
            while(front<end) {
                val at=queue[front++]
                val row=at/n
                val col=at%n
                val neighbors=intArrayOf(
                    if(row>0) at-n else -1,
                    if(row<n-1) at+n else -1,
                    if(col>0) at-1 else -1,
                    if(col<n-1) at+1 else -1
                )
                for(next in neighbors) {
                    if(next>=0 && !seen[next] && regions[next]==region) {
                        seen[next]=true
                        queue[end++]=next
                    }
                }
            }
            if(end!=regions.count { it==region })return false
        }
        return true
    }

    private fun saturation(c: Int) = maxOf(Color.red(c),Color.green(c),Color.blue(c)) -
        minOf(Color.red(c),Color.green(c),Color.blue(c))
    private fun distance(a: Int,b: Int): Int {
        val r=Color.red(a)-Color.red(b)
        val g=Color.green(a)-Color.green(b)
        val bl=Color.blue(a)-Color.blue(b)
        return r*r+g*g+bl*bl
    }
    private fun longestBand(values: DoubleArray, radius: Int, threshold: Double, minSize: Int): Span? {
        val prefix=DoubleArray(values.size+1)
        for (i in values.indices) prefix[i+1]=prefix[i]+values[i]
        var start=-1
        var best: Span?=null
        for (i in 0..values.size) {
            val active=if(i==values.size) false else {
                val l=(i-radius).coerceAtLeast(0)
                val r=(i+radius).coerceAtMost(values.lastIndex)
                (prefix[r+1]-prefix[l])/(r-l+1)>threshold
            }
            if(active && start<0) start=i
            if(!active && start>=0) {
                val candidate=Span(start,i-1)
                if(candidate.length>=minSize && (best==null || candidate.length>best.length))
                    best=candidate
                start=-1
            }
        }
        return best
    }
    private fun gutters(activity: DoubleArray, from: Int, to: Int): List<Double> {
        val output=ArrayList<Double>()
        var start=-1
        for (i in (from+5)..(to-3)) {
            val low=activity[i]<.12
            if(low && start<0) start=i
            if(!low && start>=0) {
                if(i-start>=3) output.add((start+i-1)/2.0)
                start=-1
            }
        }
        return output
    }
    private fun regular(gaps:List<Double>):Boolean {
        if(gaps.size<2)return true
        val step=(gaps.last()-gaps.first())/(gaps.size-1)
        if(step<=8)return false
        return gaps.indices.all { i -> abs(gaps[i]-(gaps.first()+i*step)) < step*.10 }
    }
}
