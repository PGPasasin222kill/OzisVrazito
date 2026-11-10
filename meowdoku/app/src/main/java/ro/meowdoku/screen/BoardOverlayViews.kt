package ro.meowdoku.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** Screen-space markers, rendered relative to the real WindowManager View location. */
class MarkerView(
    context:Context,
    val columns:IntArray,
    val board:Puzzle.Board,
    opacity:Int,
    private val shape:Int,
    private val markerColor:Int,
    var preview:Boolean
):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign=Paint.Align.CENTER
        setShadowLayer(2f,1f,1f,Color.BLACK)
    }
    private val guide=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE
        strokeWidth=2f*resources.displayMetrics.density
        color=Color.argb(195,0,230,210)
    }
    private val location=IntArray(2)

    init { setOpacity(opacity) }
    fun setOpacity(value:Int) {
        paint.color=Color.argb(value.coerceIn(20,100)*255/100,
            Color.red(markerColor),Color.green(markerColor),Color.blue(markerColor))
        invalidate()
    }
    fun confirm() { preview=false; invalidate() }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(location)
        val count=columns.size
        if(count==0)return
        val left=board.left-location[0]
        val top=board.top-location[1]
        val cw=(board.right-board.left+1f)/count
        val ch=(board.bottom-board.top+1f)/count
        val cell=minOf(cw,ch)
        if(preview) {
            canvas.drawRect(left.toFloat(),top.toFloat(),
                (board.right-location[0]).toFloat(),(board.bottom-location[1]).toFloat(),guide)
            for(i in 1 until count) {
                val dx=left+i*cw
                val dy=top+i*ch
                canvas.drawLine(dx,top.toFloat(),dx,top+count*ch,guide)
                canvas.drawLine(left.toFloat(),dy,left+count*cw,dy,guide)
            }
        }
        for(row in columns.indices) {
            val x=left+(columns[row]+.5f)*cw
            val y=top+(row+.5f)*ch
            when(shape) {
                1 -> { paint.style=Paint.Style.FILL; canvas.drawCircle(x,y,cell*.115f,paint) }
                2 -> {
                    paint.style=Paint.Style.STROKE
                    paint.strokeWidth=(cell*.07f).coerceAtLeast(3f)
                    canvas.drawCircle(x,y,cell*.27f,paint)
                    paint.style=Paint.Style.FILL
                }
                else -> {
                    paint.style=Paint.Style.FILL
                    paint.textSize=cell*if(shape==3) .58f else .63f
                    val glyph=if(shape==3) "🐱" else "★"
                    canvas.drawText(glyph,x,y-(paint.ascent()+paint.descent())/2f,paint)
                }
            }
        }
    }
}

/** Full-screen temporary calibration surface: drag corner handles or the rectangle. */
class CalibrationView(
    context:Context,
    initial:Puzzle.Board,
    private val displayWidth:Int,
    private val displayHeight:Int
):View(context) {
    var board:Puzzle.Board=initial
        private set
    private val outline=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.CYAN
        style=Paint.Style.STROKE
        strokeWidth=4f*resources.displayMetrics.density
    }
    private val handle=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.YELLOW
        style=Paint.Style.FILL
    }
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.BLACK; textSize=15f*resources.displayMetrics.scaledDensity
        textAlign=Paint.Align.CENTER
    }
    private val location=IntArray(2)
    private var mode=0
    private var fromX=0f
    private var fromY=0f
    private var original=initial

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(location)
        val l=(board.left-location[0]).toFloat()
        val t=(board.top-location[1]).toFloat()
        val r=(board.right-location[0]).toFloat()
        val b=(board.bottom-location[1]).toFloat()
        canvas.drawRect(l,t,r,b,outline)
        for(i in 1..3) {
            val x=l+(r-l)*i/4
            val y=t+(b-t)*i/4
            canvas.drawLine(x,t,x,b,outline)
            canvas.drawLine(l,y,r,y,outline)
        }
        val radius=22f*resources.displayMetrics.density
        canvas.drawCircle(l,t,radius,handle)
        canvas.drawCircle(r,b,radius,handle)
        canvas.drawText("1",l,t-(textPaint.ascent()+textPaint.descent())/2,textPaint)
        canvas.drawText("2",r,b-(textPaint.ascent()+textPaint.descent())/2,textPaint)
    }

    override fun onTouchEvent(e:MotionEvent):Boolean {
        val x=e.rawX
        val y=e.rawY
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                original=board
                fromX=x; fromY=y
                val threshold=70f*resources.displayMetrics.density
                mode=when {
                    abs(x-board.left)<threshold && abs(y-board.top)<threshold -> 1
                    abs(x-board.right)<threshold && abs(y-board.bottom)<threshold -> 2
                    x in board.left.toFloat()..board.right.toFloat() &&
                        y in board.top.toFloat()..board.bottom.toFloat() -> 3
                    else -> 0
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx=(x-fromX).toInt()
                val dy=(y-fromY).toInt()
                board=when(mode) {
                    1 -> Puzzle.Board(
                        (original.left+dx).coerceIn(1,original.right-120),
                        (original.top+dy).coerceIn(1,original.bottom-120),
                        original.right,original.bottom)
                    2 -> Puzzle.Board(original.left,original.top,
                        (original.right+dx).coerceIn(original.left+120,displayWidth-2),
                        (original.bottom+dy).coerceIn(original.top+120,displayHeight-2))
                    3 -> {
                        val moveX=(original.left+dx).coerceIn(1,displayWidth-(original.right-original.left)-2)-original.left
                        val moveY=(original.top+dy).coerceIn(1,displayHeight-(original.bottom-original.top)-2)-original.top
                        Puzzle.Board(original.left+moveX,original.top+moveY,
                            original.right+moveX,original.bottom+moveY)
                    }
                    else -> board
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                mode=0
                return true
            }
        }
        return true
    }
}
