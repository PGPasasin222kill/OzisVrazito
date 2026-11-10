package ro.meowdoku.screen

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.roundToInt

class OverlayService: Service() {
    private lateinit var wm: WindowManager
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var controls:View?=null
    private var overlay:MarksView?=null
    private var statusText:TextView?=null
    private val handler=Handler(Looper.getMainLooper())
    private var scanBusy=false
    private var stopped=false
    private var scanGeneration=0
    private var clearJob:Runnable?=null
    private var width=0
    private var height=0
    private var density=0

    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val channel=NotificationChannel("capture","Meowdoku scanare ecran",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Only one capture session and one set of controls may exist.
        if (controls != null) return START_NOT_STICKY
        val notice=Notification.Builder(this,"capture")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Meowdoku Screen Solver")
            .setContentText("Captura activă. Apasă MEOW ca să scanezi.")
            .build()
        if(Build.VERSION.SDK_INT>=29) {
            startForeground(11,notice,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(11,notice)
        val token=if(Build.VERSION.SDK_INT>=33)
            intent?.getParcelableExtra("resultData",Intent::class.java)
        else intent?.getParcelableExtra<Intent>("resultData")
        if(token==null) { stopSelf(); return START_NOT_STICKY }
        val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=m.getMediaProjection(intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED,token)
        projection?.registerCallback(object:MediaProjection.Callback() {
            override fun onStop() { handler.post { stopSelf() } }
        },handler)
        val metrics=android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        width=metrics.widthPixels
        height=metrics.heightPixels
        density=metrics.densityDpi
        reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
        display=projection?.createVirtualDisplay("meowdoku",width,height,density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
        showControls()
        return START_NOT_STICKY
    }
    private fun dp(value:Int):Int =
        (value * resources.displayMetrics.density).toInt()

    private fun setStatus(message:String) {
        if (UserPrefs.showMessages(this)) statusText?.text=message
    }

    private fun appToast(message:String, long:Boolean=false) {
        if (UserPrefs.showMessages(this)) Toast.makeText(
            this,message,if(long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    private fun showControls() {
        // Default: two small floating buttons. More actions are in the gear menu.
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
        }
        val top=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.END
        }
        val size=UserPrefs.sizeDp(this)
        fun bubble(symbol:String, label:String, action:()->Unit):TextView =
            TextView(this).apply {
                text=symbol
                textSize=(size*.46f).coerceIn(11f,22f)
                gravity=Gravity.CENTER
                contentDescription=label
                setTextColor(Color.WHITE)
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(224,40,39,46))
                    shape=android.graphics.drawable.GradientDrawable.OVAL
                }
                layoutParams=LinearLayout.LayoutParams(dp(size),dp(size)).apply {
                    marginEnd=dp(4)
                }
                setOnClickListener { action() }
            }
        val menu=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
            setPadding(dp(8),dp(6),dp(8),dp(8))
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(231,42,41,47))
                cornerRadius=dp(10).toFloat()
            }
        }
        val scanButton=bubble("🐱","Scanează tabla") {
            menu.visibility=View.GONE
            scan()
        }
        val gearButton=bubble("⚙","Setări rapide") {
            menu.visibility=if(menu.visibility==View.VISIBLE) View.GONE else View.VISIBLE
        }
        top.addView(scanButton)
        top.addView(gearButton)
        root.addView(top)

        val info=TextView(this).apply {
            text="Apasă 🐱 pentru scanare"
            textSize=11f
            setTextColor(Color.WHITE)
            setPadding(dp(5),dp(3),dp(5),dp(3))
            maxWidth=dp(160)
            visibility=if(UserPrefs.showMessages(this@OverlayService)) View.VISIBLE else View.GONE
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(205,42,41,47))
                cornerRadius=dp(6).toFloat()
            }
        }
        root.addView(info)
        statusText=info

        fun menuButton(title:String, onClick:()->Unit):TextView =
            TextView(this).apply {
                text=title
                textSize=14f
                setTextColor(Color.WHITE)
                gravity=Gravity.CENTER
                setPadding(dp(8),dp(11),dp(8),dp(11))
                setOnClickListener { onClick() }
            }
        menu.addView(menuButton("ASCUNDE ★") {
            scanGeneration++
            scanBusy=false
            clearMarks()
            setStatus("Marcajele au fost ascunse")
            menu.visibility=View.GONE
        })
        val messageButton=menuButton(
            if (UserPrefs.showMessages(this)) "Mesaje: ON" else "Mesaje: OFF"
        ) {
            val enabled=!UserPrefs.showMessages(this)
            UserPrefs.setShowMessages(this,enabled)
            info.visibility=if(enabled) View.VISIBLE else View.GONE
            if(enabled) info.text="Mesajele sunt pornite"
        }
        messageButton.setOnClickListener {
            val enabled=!UserPrefs.showMessages(this)
            UserPrefs.setShowMessages(this,enabled)
            info.visibility=if(enabled) View.VISIBLE else View.GONE
            messageButton.text=if(enabled) "Mesaje: ON" else "Mesaje: OFF"
            if(enabled) info.text="Mesajele sunt pornite"
        }
        menu.addView(messageButton)
        val sizeLabel=menuButton("Mărime: ${UserPrefs.sizeDp(this)} dp") {}
        fun updateSize(change:Int) {
            val newSize=(UserPrefs.sizeDp(this)+change).coerceIn(24,64)
            UserPrefs.setSizeDp(this,newSize)
            for (button in listOf(scanButton,gearButton)) {
                button.layoutParams=LinearLayout.LayoutParams(dp(newSize),dp(newSize)).apply {
                    marginEnd=dp(4)
                }
                button.textSize=(newSize*.46f).coerceIn(11f,22f)
            }
            sizeLabel.text="Mărime: $newSize dp"
        }
        val sizeRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        sizeRow.addView(menuButton("−") { updateSize(-4) },
            LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        sizeRow.addView(sizeLabel,
            LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,2f))
        sizeRow.addView(menuButton("+") { updateSize(4) },
            LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        menu.addView(sizeRow)
        menu.addView(menuButton("STOP") {
            scanGeneration++
            scanBusy=false
            clearMarks()
            stopSelf()
        })
        root.addView(menu)
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.START
            x=UserPrefs.overlayX(this@OverlayService).let {
                if(it>=0) it else (width-dp(230)).coerceAtLeast(0)
            }
            y=UserPrefs.overlayY(this@OverlayService).let {
                if(it>=0) it else dp(90)
            }
            x=x.coerceIn(0,(width-dp(100)).coerceAtLeast(0))
            y=y.coerceIn(0,(height-dp(100)).coerceAtLeast(0))
        }
        controls=root
        wm.addView(root,params)
        // Drag either circular button to move the complete overlay.
        fun allowDragging(button:View) {
            button.setOnTouchListener(object:View.OnTouchListener {
                var startX=0f
                var startY=0f
                var originalX=0
                var originalY=0
                var dragged=false
                override fun onTouch(view:View?,event:MotionEvent):Boolean {
                    when(event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX=event.rawX; startY=event.rawY
                            originalX=params.x; originalY=params.y
                            dragged=false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx=event.rawX-startX
                            val dy=event.rawY-startY
                            if(!dragged && (kotlin.math.abs(dx)>dp(6) || kotlin.math.abs(dy)>dp(6)))
                                dragged=true
                            if(dragged) {
                                params.x=(originalX+dx.toInt()).coerceIn(0,(width-root.width).coerceAtLeast(0))
                                params.y=(originalY+dy.toInt()).coerceIn(0,(height-root.height).coerceAtLeast(0))
                                try { wm.updateViewLayout(root,params) } catch(_:Exception) {}
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if(dragged) UserPrefs.saveOverlayPosition(this@OverlayService,params.x,params.y)
                            else view?.performClick()
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> return true
                    }
                    return false
                }
            })
        }
        allowDragging(scanButton)
        allowDragging(gearButton)
    }
    private fun clearMarks() {
        clearJob?.let { handler.removeCallbacks(it) }
        clearJob=null
        overlay?.let {
            try { wm.removeViewImmediate(it) } catch (_:Exception) { }
        }
        overlay=null
    }
    private fun scan() {
        if (stopped || scanBusy) return
        scanBusy=true
        val id=++scanGeneration
        clearMarks()
        setStatus("Scanez...")
        // Hide our own buttons while reading colors, including after dragging over the grid.
        controls?.visibility=View.INVISIBLE
        // Allow time for WindowManager to remove the previous image from the captured screen.
        handler.postDelayed({ if (!stopped && id==scanGeneration) capture(0,id) },650)
    }
    private fun capture(attempt:Int=0, id:Int) {
        if (stopped || id!=scanGeneration) return
        val img=reader?.acquireLatestImage()
        if(img==null) {
            if(attempt<8) {
                handler.postDelayed({ capture(attempt+1,id) },180)
            } else {
                scanBusy=false
                controls?.visibility=View.VISIBLE
                setStatus("Nu am primit captura. Încearcă din nou.")
            }
            return
        }
        val bitmap=try {
            val p=img.planes[0]
            val pixel=p.pixelStride
            val row=p.rowStride
            val padded=Bitmap.createBitmap(width+(row-pixel*width)/pixel,height,Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(p.buffer)
            val cropped=Bitmap.createBitmap(padded,0,0,width,height)
            padded.recycle()
            cropped
        } catch(e:Exception) { null } finally { img.close() }
        controls?.visibility=View.VISIBLE
        if(bitmap==null) {
            scanBusy=false
            setStatus("Nu pot citi captura ecranului.")
            return
        }
        setStatus("Scanez tabla...")
        Thread {
            val scan=try { Puzzle.detect(bitmap,UserPrefs.verifyResult(this)) }
                catch(e:Exception) { Puzzle.Scan(null,"Eroare la scanare: "+e.javaClass.simpleName) }
            bitmap.recycle()
            handler.post {
                if (stopped || id!=scanGeneration) return@post
                scanBusy=false
                val result=scan.result
                if(result==null) {
                    setStatus(scan.message)
                    appToast(scan.message,true)
                    return@post
                }
                // Never stack windows: one puzzle => exactly N stars.
                clearMarks()
                val marks=MarksView(this,result.columns,result.board,UserPrefs.starOpacity(this))
                val params=WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity=Gravity.TOP or Gravity.START
                    // Android 12+ blocks touch pass-through from untrusted
                    // overlays if window opacity exceeds 0.8. 0.65 keeps
                    // stars visible while allowing taps on the game below.
                    alpha=0.79f
                }
                overlay=marks
                wm.addView(marks,params)
                setStatus(scan.message+" — "+result.size+" steluțe")
                // Marks disappear automatically, even if user forgets to press Hide.
                val duration=UserPrefs.duration(this)
                if(duration>0) {
                    val expiration=Runnable {
                        if(!stopped && id==scanGeneration) {
                            clearMarks()
                            setStatus("Marcajele au dispărut. Apasă MEOW pentru alt nivel.")
                        }
                    }
                    clearJob=expiration
                    handler.postDelayed(expiration,duration*1000L)
                }
                appToast(if(duration>0) "Gata: "+result.size+"×"+result.size+" — "+duration+" secunde"
                    else "Gata: "+result.size+"×"+result.size+" — ascunde manual din ⚙")
            }
        }.start()
    }
    override fun onDestroy() {
        stopped=true
        scanGeneration++
        scanBusy=false
        clearMarks()
        controls?.let { try { wm.removeViewImmediate(it) } catch (_:Exception) { } }
        controls=null
        handler.removeCallbacksAndMessages(null)
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
    private class MarksView(ctx:Context,val columns:IntArray,val board:Puzzle.Board,opacity:Int):View(ctx) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.YELLOW
            style=Paint.Style.FILL
            textAlign=Paint.Align.CENTER
            setShadowLayer(4f,1f,2f,Color.BLACK)
        }
        init { setOpacity(opacity) }
        fun setOpacity(value:Int) {
            paint.alpha=value.coerceIn(20,100)*255/100
            invalidate()
        }
        override fun onDraw(canvas:Canvas) {
            super.onDraw(canvas)
            val b=board.asFloats()
            val cw=(b[2]-b[0])/columns.size
            val ch=(b[3]-b[1])/columns.size
            // The captured bitmap is in absolute screen coordinates. WindowManager
            // may position a full-screen overlay below the Samsung status bar.
            // Convert absolute positions into coordinates local to this View.
            val location=IntArray(2)
            getLocationOnScreen(location)
            paint.textSize=minOf(cw,ch)*0.66f
            for(r in columns.indices) {
                val cx=b[0]+(columns[r]+0.5f)*cw-location[0]
                val cy=b[1]+(r+0.5f)*ch-location[1]
                canvas.drawText("★",cx,cy-(paint.ascent()+paint.descent())/2f,paint)
            }
        }
    }
}
