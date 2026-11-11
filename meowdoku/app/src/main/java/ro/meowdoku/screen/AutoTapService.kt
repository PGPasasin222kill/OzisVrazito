package ro.meowdoku.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Only dispatches gestures after the user explicitly enables CatPilot AUTO mode
 * and triggers MEOW on a solved board. No unattended/autonomous tapping.
 */
class AutoTapService : AccessibilityService() {
    companion object {
        @Volatile private var current: AutoTapService? = null

        fun isReady():Boolean=current!=null
        fun cancel() { current?.cancelSequence() }

        fun run(result:Puzzle.Result,alreadyPlaced:BooleanArray,onResult:(Boolean,String)->Unit):Boolean {
            val service=current ?: return false
            return service.startSequence(result,alreadyPlaced,onResult)
        }
    }

    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    private var busy=false

    override fun onServiceConnected() {
        super.onServiceConnected()
        current=this
    }

    override fun onAccessibilityEvent(event:AccessibilityEvent?) { /* No screen contents collected. */ }
    override fun onInterrupt() { cancelSequence() }

    private fun cancelSequence() {
        generation++
        busy=false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        cancelSequence()
        if(current===this) current=null
        super.onDestroy()
    }

    private fun startSequence(result:Puzzle.Result,alreadyPlaced:BooleanArray,onResult:(Boolean,String)->Unit):Boolean {
        if(busy || result.size !in 3..24 || result.columns.any { it !in 0 until result.size })
            return false
        val target=rootInActiveWindow?.packageName?.toString()
        if(target==packageName) return false
        val board=result.board
        val count=result.size
        val cw=(board.right-board.left+1f)/count
        val ch=(board.bottom-board.top+1f)/count
        if(cw<12f || ch<12f) return false
        busy=true
        val token=++generation
        var index=0
        var tapped=0
        fun finish(ok:Boolean,message:String) {
            if(token!=generation)return
            busy=false
            onResult(ok,message)
        }
        fun next() {
            if(token!=generation)return
            if(index>=count) {
                finish(true,"AUTO: apăsate "+tapped+" căsuțe; "+(count-tapped)+" deja ocupate.")
                return
            }
            if(index<alreadyPlaced.size && alreadyPlaced[index]) {
                index++
                handler.postDelayed({ next() },60L)
                return
            }
            val col=result.columns[index]
            val x=board.left+(col+.5f)*cw
            val y=board.top+(index+.5f)*ch
            // Coordinates are in physical display pixels, as captured by MediaProjection.
            val path=Path().apply { moveTo(x,y) }
            val gesture=GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path,0,85))
                .build()
            val dispatched=dispatchGesture(gesture,
                object:GestureResultCallback() {
                    override fun onCompleted(gestureDescription:GestureDescription?) {
                        if(token!=generation)return
                        index++
                        tapped++
                        handler.postDelayed({ next() },190L)
                    }
                    override fun onCancelled(gestureDescription:GestureDescription?) {
                        finish(false,"AUTO întrerupt după "+index+" căsuțe.")
                    }
                },handler)
            if(!dispatched) finish(false,"Android nu a acceptat atingerea AUTO.")
        }
        // Give the application overlay time to become invisible.
        handler.postDelayed({ next() },420L)
        return true
    }
}
