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
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService: Service() {
    companion object {
        const val ACTION_CLEAR = "ro.meowdoku.screen.CLEAR_OVERLAY"
        const val ACTION_SCAN = "ro.meowdoku.screen.SCAN"
        const val ACTION_CALIBRATE = "ro.meowdoku.screen.CALIBRATE"
        const val ACTION_STOP = "ro.meowdoku.screen.STOP_OVERLAY"
    }
    private lateinit var wm: WindowManager
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var controls:View?=null
    private var overlay:MarkerView?=null
    private var calibrationView:CalibrationView?=null
    private var calibrationButtons:View?=null
    private var lastResult:Puzzle.Result?=null
    private var signature:IntArray?=null
    private var monitorJob:Runnable?=null
    private var changeFrames=0
    private var statusText:TextView?=null
    private val handler=Handler(Looper.getMainLooper())
    private var scanBusy=false
    private var stopped=false
    private var scanGeneration=0
    private var clearJob:Runnable?=null
    private var width=0
    private var height=0
    private var density=0

    // Continuous AUTO only runs after a user-initiated MEOW has just solved a board.
    private enum class AutoPhase { IDLE, WAITING_RESULT, DISMISSING_RESULT, WAITING_BOARD, CHECKING_BOARD }
    private var autoPhase=AutoPhase.IDLE
    private var autoLoopEpoch=0
    private var autoLoopTask:Runnable?=null
    private var autoLoopLastKind=CompletionScreens.Kind.UNKNOWN
    private var autoLoopStableFrames=0
    private var autoLoopSawPopup=false
    private var autoLoopDismissCount=0
    private var autoLoopBoardRetries=0
    private var autoLoopDeadline=0L
    private var autoLoopLastDismiss=0L
    private var autoLoopBusy=false
    private var autoLoopBaseline:IntArray?=null
    private var autoLoopBaselineSize=0

    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val channel=NotificationChannel("capture","CatPilot — captură ecran",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // System UI / app emergency commands work without asking for screen share again.
        if (intent?.action==ACTION_STOP) {
            cancelAutoContinue()
            scanGeneration++
            scanBusy=false
            AutoTapService.cancel()
            clearMarks()
            stopCalibration(false)
            controls?.visibility=View.VISIBLE
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action==ACTION_CLEAR) {
            cancelAutoContinue()
            scanGeneration++
            scanBusy=false
            AutoTapService.cancel()
            clearMarks()
            stopCalibration(false)
            controls?.visibility=View.VISIBLE
            if(projection==null) stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ACTION_SCAN) {
            if(projection!=null) scan() else stopSelf()
            return START_NOT_STICKY
        }
        if(intent?.action==ACTION_CALIBRATE) {
            if(projection!=null) beginCalibration() else stopSelf()
            return START_NOT_STICKY
        }
        // Only one capture session and one set of controls may exist.
        if (controls != null) return START_NOT_STICKY
        fun pending(action:String, request:Int)=PendingIntent.getService(
            this,request,Intent(this,OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice=Notification.Builder(this,"capture")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("CatPilot v1.1 • AUTO opțional")
            .setContentText("Captura activă • SCAN / ASCUNDE / OPREȘTE")
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_search,"SCANEAZĂ",pending(ACTION_SCAN,101)).build())
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"ASCUNDE",pending(ACTION_CLEAR,102)).build())
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_delete,"OPREȘTE",pending(ACTION_STOP,103)).build())
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
            cancelAutoContinue()
            scanGeneration++
            scanBusy=false
            AutoTapService.cancel()
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
        val autoButton=menuButton(if(UserPrefs.autoSolver(this)) "AUTO: ON" else "AUTO: OFF") {}
        autoButton.setOnClickListener {
            val next=!UserPrefs.autoSolver(this)
            if(!next) {
                cancelAutoContinue()
                AutoTapService.cancel()
                controls?.visibility=View.VISIBLE
            }
            UserPrefs.setAutoSolver(this,next)
            autoButton.text=if(next) "AUTO: ON" else "AUTO: OFF"
            if(next && !AutoTapService.isReady()) {
                menu.visibility=View.GONE
                startActivity(Intent(this,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                Toast.makeText(this,"Activează CatPilot în Accesibilitate ca să funcționeze AUTO.",Toast.LENGTH_LONG).show()
            }
        }
        menu.addView(autoButton)
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
        menu.addView(menuButton("CALIBREAZĂ TABLA") {
            menu.visibility=View.GONE
            beginCalibration()
        })
        menu.addView(menuButton("SETĂRI COMPLETE") {
            menu.visibility=View.GONE
            startActivity(Intent(this,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        })
        menu.addView(menuButton("STOP") {
            cancelAutoContinue()
            scanGeneration++
            scanBusy=false
            AutoTapService.cancel()
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
    private fun beginCalibration() {
        if(stopped || projection==null) return
        cancelAutoContinue()
        AutoTapService.cancel()
        scanGeneration++
        scanBusy=false
        clearMarks()
        stopCalibration(false)
        controls?.visibility=View.GONE
        val saved=UserPrefs.manualBoard(this,width,height)
        val original=saved ?: lastResult?.board ?: run {
            val margin=(width*.045f).toInt()
            val side=width-2*margin
            val top=((height-side)/2).coerceAtLeast(0)
            Puzzle.Board(margin,top,width-margin,top+side)
        }
        val frame=CalibrationView(this,original,width,height)
        val surface=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.START }
        calibrationView=frame
        wm.addView(frame,surface)
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(10),dp(8),dp(10),dp(8))
            setBackgroundColor(Color.argb(240,39,39,50))
        }
        panel.addView(TextView(this).apply {
            text="Trage punctele 1 și 2 pe colțurile tablei. Sau mută dreptunghiul."
            textSize=13f
            setTextColor(Color.WHITE)
        })
        val buttons=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text="SALVEAZĂ"
            setOnClickListener { stopCalibration(true) }
        })
        buttons.addView(Button(this).apply {
            text="ANULEAZĂ"
            setOnClickListener { stopCalibration(false) }
        })
        panel.addView(buttons)
        val p=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; y=dp(70) }
        calibrationButtons=panel
        wm.addView(panel,p)
    }

    private fun stopCalibration(save:Boolean) {
        val view=calibrationView
        if(save && view!=null) {
            UserPrefs.saveBoard(this,view.board,width,height)
            UserPrefs.saveDiagnosis(this,"Calibrare salvată. Scanează din nou pentru soluție.",0,0)
            setStatus("Calibrare salvată. Scanează din nou.")
        }
        calibrationButtons?.let { try { wm.removeViewImmediate(it) } catch(_:Exception) {} }
        calibrationButtons=null
        calibrationView?.let { try { wm.removeViewImmediate(it) } catch(_:Exception) {} }
        calibrationView=null
        if(!stopped) controls?.visibility=View.VISIBLE
    }

    private fun clearMarks() {
        monitorJob?.let { handler.removeCallbacks(it) }
        monitorJob=null
        signature=null
        changeFrames=0
        clearJob?.let { handler.removeCallbacks(it) }
        clearJob=null
        overlay?.let {
            try { wm.removeViewImmediate(it) } catch (_:Exception) { }
        }
        overlay=null
    }
    private fun scan() {
        if (stopped || scanBusy) return
        cancelAutoContinue()
        AutoTapService.cancel()
        scanBusy=true
        val id=++scanGeneration
        clearMarks()
        stopCalibration(false)
        setStatus("Scanez...")
        // Hide our own buttons while reading colors, including after dragging over the grid.
        controls?.visibility=View.INVISIBLE
        // Allow time for WindowManager to remove the previous image from the captured screen.
        handler.postDelayed({ if (!stopped && id==scanGeneration) capture(0,id) },650)
    }
    private fun imageBitmap(img:android.media.Image):Bitmap? = try {
        val p=img.planes[0]
        val pixel=p.pixelStride
        val row=p.rowStride
        val paddedWidth=width+(row-pixel*width)/pixel
        val padded=Bitmap.createBitmap(paddedWidth,height,Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(p.buffer)
        val cropped=Bitmap.createBitmap(padded,0,0,width,height)
        padded.recycle()
        cropped
    } catch(_:Exception) { null }

    // Tile corner samples are outside the middle where markers and cats appear.
    private fun boardFingerprint(bmp:Bitmap,board:Puzzle.Board,n:Int):IntArray {
        val w=(board.right-board.left+1f)/n
        val h=(board.bottom-board.top+1f)/n
        return IntArray(n*n) { index ->
            val r=index/n
            val col=index%n
            val x=(board.left+(col+.2f)*w).toInt().coerceIn(0,bmp.width-1)
            val y=(board.top+(r+.2f)*h).toInt().coerceIn(0,bmp.height-1)
            val pixel=bmp.getPixel(x,y)
            val red=Color.red(pixel) shr 4
            val green=Color.green(pixel) shr 4
            val blue=Color.blue(pixel) shr 4
            (red shl 8) or (green shl 4) or blue
        }
    }

    private fun watchLevelChange(id:Int, board:Puzzle.Board, n:Int) {
        if(!UserPrefs.autoHideLevelChange(this) || signature==null) return
        monitorJob?.let { handler.removeCallbacks(it) }
        val check=object:Runnable {
            override fun run() {
                if(stopped || id!=scanGeneration || overlay==null || scanBusy) return
                val img=reader?.acquireLatestImage()
                if(img!=null) {
                    val image=try { imageBitmap(img) } finally { img.close() }
                    if(image!=null) {
                        try {
                            val current=boardFingerprint(image,board,n)
                            val old=signature
                            if(old!=null && old.size==current.size) {
                                val count=old.indices.count {
                                    val a=old[it]; val b=current[it]
                                    val dr=abs((a shr 8)-(b shr 8))
                                    val dg=abs(((a shr 4) and 15)-((b shr 4) and 15))
                                    val db=abs((a and 15)-(b and 15))
                                    dr+dg+db>=4
                                }
                                // Two different consecutive frames avoid transient animations.
                                if(count>=maxOf(4,n*n/4)) changeFrames++ else changeFrames=0
                                if(changeFrames>=2) {
                                    clearMarks()
                                    setStatus("Tabla s-a schimbat: am ascuns marcajele vechi.")
                                    return
                                }
                            }
                        } catch(_:Exception) {} finally { image.recycle() }
                    }
                }
                if(!stopped && id==scanGeneration && overlay!=null) {
                    handler.postDelayed(this,2300L)
                }
            }
        }
        monitorJob=check
        handler.postDelayed(check,2300L)
    }

    private fun activateMarkers(id:Int, result:Puzzle.Result) {
        // Immediate display; no confirmation step. Keep automatic expiration,
        // level-change monitoring and the emergency Hide/Stop actions.
        if(stopped || id!=scanGeneration || overlay==null) return
        setStatus("Soluție "+result.size+"×"+result.size+" afișată. Ascunde din ⚙ sau notificare.")
        val duration=UserPrefs.duration(this)
        if(duration>0) {
            val expiration=Runnable {
                if(!stopped && id==scanGeneration) {
                    clearMarks()
                    setStatus("Marcajele au dispărut.")
                }
            }
            clearJob=expiration
            handler.postDelayed(expiration,duration*1000L)
        }
        appToast(if(duration>0) "Soluție "+result.size+"×"+result.size+" — "+duration+" secunde."
            else "Soluție "+result.size+"×"+result.size+" — ascunde manual din ⚙.")
        watchLevelChange(id,result.board,result.size)
    }

    private fun displayResult(id:Int,result:Puzzle.Result,fingerprint:IntArray?) {
        if(stopped || id!=scanGeneration) return
        clearMarks()
        lastResult=result
        signature=fingerprint
        val marks=MarkerView(this,result.columns,result.board,
            UserPrefs.starOpacity(this),UserPrefs.markerShape(this),
            UserPrefs.markerColor(this))
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity=Gravity.TOP or Gravity.START
            // Keep the window below Android's obscured-touch threshold.
            alpha=0.79f
        }
        overlay=marks
        wm.addView(marks,params)
        activateMarkers(id,result)
    }

    /**
     * AUTO is user opt-in and only uses the unique verified result. Existing cats
     * are recognized conservatively by their dark icon and lighter eye details.
     * When recognition is uncertain the user can use the manual mode instead.
     */
    private fun placedCatRows(bmp:Bitmap, result:Puzzle.Result):BooleanArray {
        val n=result.size
        val board=result.board
        val cw=(board.right-board.left+1f)/n
        val ch=(board.bottom-board.top+1f)/n
        return BooleanArray(n) { r ->
            val col=result.columns[r]
            var dark=0
            var light=0
            var total=0
            for(iy in 0..10) for(ix in 0..10) {
                val fx=.25f+ix*.05f
                val fy=.25f+iy*.05f
                val x=(board.left+(col+fx)*cw).toInt().coerceIn(0,bmp.width-1)
                val y=(board.top+(r+fy)*ch).toInt().coerceIn(0,bmp.height-1)
                val p=bmp.getPixel(x,y)
                val red=Color.red(p)
                val green=Color.green(p)
                val blue=Color.blue(p)
                if(red<78 && green<78 && blue<78) dark++
                if(red>198 && green>198 && blue>198) light++
                total++
            }
            dark>=total/7 && light>=total/35
        }
    }

    private fun cancelAutoContinue() {
        autoLoopEpoch++
        autoLoopTask?.let { handler.removeCallbacks(it) }
        autoLoopTask=null
        autoLoopBusy=false
        autoLoopSawPopup=false
        autoLoopLastKind=CompletionScreens.Kind.UNKNOWN
        autoLoopStableFrames=0
        autoPhase=AutoPhase.IDLE
    }

    private fun stopAutoContinue(reason:String) {
        cancelAutoContinue()
        controls?.visibility=View.VISIBLE
        setStatus(reason)
        UserPrefs.saveDiagnosis(this,reason,0,0)
        appToast(reason,true)
    }

    private fun scheduleAutoContinue(id:Int,epoch:Int,waitMs:Long) {
        if(stopped || id!=scanGeneration || epoch!=autoLoopEpoch) return
        autoLoopTask?.let { handler.removeCallbacks(it) }
        val task=Runnable { pollAutoContinue(id,epoch) }
        autoLoopTask=task
        handler.postDelayed(task,waitMs.coerceAtLeast(250L))
    }

    private fun startAutoContinue(id:Int,previous:Puzzle.Result,fingerprint:IntArray?) {
        cancelAutoContinue()
        if(!UserPrefs.autoSolver(this) || !UserPrefs.autoContinue(this) ||
            !AutoTapService.isReady() || stopped || id!=scanGeneration)return
        autoPhase=AutoPhase.WAITING_RESULT
        autoLoopDismissCount=0
        autoLoopBoardRetries=0
        autoLoopSawPopup=false
        autoLoopBaseline=fingerprint
        autoLoopBaselineSize=previous.size
        autoLoopDeadline=SystemClock.elapsedRealtime()+60000L
        setStatus("AUTO: aștept ecranul de final.")
        scheduleAutoContinue(id,autoLoopEpoch,1150L)
    }

    private fun pollAutoContinue(id:Int,epoch:Int) {
        if(stopped || id!=scanGeneration || epoch!=autoLoopEpoch)return
        if(!UserPrefs.autoContinue(this) || !UserPrefs.autoSolver(this) ||
           !AutoTapService.isReady()) {
            stopAutoContinue("AUTO-continuare a fost oprită.")
            return
        }
        if(SystemClock.elapsedRealtime()>autoLoopDeadline) {
            stopAutoContinue("AUTO s-a oprit: următorul nivel nu a apărut.")
            return
        }
        if(autoLoopBusy)return
        val image=reader?.acquireLatestImage()
        if(image==null) {
            scheduleAutoContinue(id,epoch,550L)
            return
        }
        val bmp=try { imageBitmap(image) } finally { image.close() }
        if(bmp==null) {
            scheduleAutoContinue(id,epoch,700L)
            return
        }
        val kind=try { CompletionScreens.classify(bmp) }
            catch(_:Exception) { CompletionScreens.Kind.UNKNOWN }
        if(kind!=CompletionScreens.Kind.UNKNOWN) {
            bmp.recycle()
            if(kind==autoLoopLastKind) autoLoopStableFrames++
            else {
                autoLoopLastKind=kind
                autoLoopStableFrames=1
            }
            // Even with Safe Mode off, never tap a screen classified UNKNOWN.
            val framesNeeded=if(UserPrefs.safeAuto(this))2 else 1
            if(autoLoopStableFrames<framesNeeded) {
                scheduleAutoContinue(id,epoch,560L)
                return
            }
            autoLoopSawPopup=true
            if(autoLoopDismissCount>=UserPrefs.autoRetryLimit(this)) {
                stopAutoContinue("AUTO oprit: prea multe ecrane de final consecutive.")
                return
            }
            val target=CompletionScreens.tapPoint(kind)
            if(target==null) {
                stopAutoContinue("AUTO nu recunoaște butonul de continuare.")
                return
            }
            autoPhase=AutoPhase.DISMISSING_RESULT
            autoLoopBusy=true
            val sent=AutoTapService.tapFraction(target.first,target.second) { ok ->
                if(stopped || id!=scanGeneration || epoch!=autoLoopEpoch)return@tapFraction
                autoLoopBusy=false
                if(!ok) {
                    stopAutoContinue("AUTO: Android a refuzat atingerea de continuare.")
                } else {
                    autoLoopDismissCount++
                    autoLoopLastDismiss=SystemClock.elapsedRealtime()
                    autoLoopLastKind=CompletionScreens.Kind.UNKNOWN
                    autoLoopStableFrames=0
                    autoPhase=AutoPhase.WAITING_BOARD
                    setStatus("AUTO: am închis "+if(kind==CompletionScreens.Kind.LEADERBOARD)
                        "Leaderboard." else "Flawless.")
                    scheduleAutoContinue(id,epoch,UserPrefs.continueDelayMs(this).toLong())
                }
            }
            if(!sent) {
                autoLoopBusy=false
                stopAutoContinue("AUTO: atingerea nu a putut fi trimisă.")
            }
            return
        }
        // No blind fallback: we only look for the next board after an identified
        // Leaderboard/Flawless screen has been dismissed successfully.
        autoLoopLastKind=CompletionScreens.Kind.UNKNOWN
        autoLoopStableFrames=0
        if(!autoLoopSawPopup) {
            bmp.recycle()
            scheduleAutoContinue(id,epoch,700L)
            return
        }
        val sinceDismiss=SystemClock.elapsedRealtime()-autoLoopLastDismiss
        if(sinceDismiss<UserPrefs.continueDelayMs(this)) {
            bmp.recycle()
            scheduleAutoContinue(id,epoch,500L)
            return
        }
        if(autoLoopBoardRetries>=UserPrefs.autoRetryLimit(this)) {
            bmp.recycle()
            stopAutoContinue("AUTO s-a oprit: nu poate recunoaște tabla nouă.")
            return
        }
        autoPhase=AutoPhase.CHECKING_BOARD
        autoLoopBusy=true
        Thread {
            val scan=try {
                Puzzle.detect(bmp,true,UserPrefs.manualBoard(this,width,height))
            } catch(_:Exception) { Puzzle.Scan(null,"Eroare la recunoașterea nivelului nou.") }
            val fp=try { scan.result?.let { boardFingerprint(bmp,it.board,it.size) } }
                catch(_:Exception) { null }
            val placed=try { scan.result?.let { placedCatRows(bmp,it) } ?: BooleanArray(0) }
                catch(_:Exception) { BooleanArray(0) }
            bmp.recycle()
            handler.post {
                if(stopped || id!=scanGeneration || epoch!=autoLoopEpoch)return@post
                autoLoopBusy=false
                if(scan.result!=null) {
                    val old=autoLoopBaseline
                    val fresh=fp
                    val sameLayout=old!=null && fresh!=null &&
                        autoLoopBaselineSize==scan.result.size && old.size==fresh.size
                    val changed=if(sameLayout && old!=null && fresh!=null)
                        old.indices.count { i ->
                            val a=old[i]; val b=fresh[i]
                            val diff=abs((a shr 8)-(b shr 8))+
                                abs(((a shr 4) and 15)-((b shr 4) and 15))+
                                abs((a and 15)-(b and 15))
                            diff>=4
                        } else Int.MAX_VALUE
                    if(!sameLayout || changed>=maxOf(3,scan.result.size*scan.result.size/10)) {
                        cancelAutoContinue()
                        val nextId=++scanGeneration
                        UserPrefs.saveDiagnosis(this,"AUTO: nivel nou "+scan.result.size+"×"+scan.result.size,
                            scan.gridSize,scan.colors)
                        useSolution(nextId,scan.result,fp,placed)
                        return@post
                    }
                }
                autoLoopBoardRetries++
                autoPhase=AutoPhase.WAITING_BOARD
                scheduleAutoContinue(id,epoch,1000L)
            }
        }.start()
    }

    private fun useSolution(id:Int,result:Puzzle.Result,fp:IntArray?,alreadyPlaced:BooleanArray) {
        if(stopped || id!=scanGeneration)return
        cancelAutoContinue()
        if(!UserPrefs.autoSolver(this)) {
            displayResult(id,result,fp)
            return
        }
        if(!AutoTapService.isReady()) {
            setStatus("AUTO necesită serviciul CatPilot din Accesibilitate.")
            Toast.makeText(this,"Activează CatPilot în Setări → Accesibilitate. Arăt marcajele manual.",Toast.LENGTH_LONG).show()
            displayResult(id,result,fp)
            return
        }
        clearMarks()
        controls?.visibility=View.INVISIBLE
        val accepted=AutoTapService.run(result,alreadyPlaced) { success,message ->
            if(stopped || id!=scanGeneration)return@run
            controls?.visibility=View.VISIBLE
            setStatus(message)
            UserPrefs.saveDiagnosis(this,message,result.size,result.size)
            if(success) {
                startAutoContinue(id,result,fp)
            } else {
                // Partial auto placement may need inspection; show remaining suggestion.
                displayResult(id,result,fp)
                Toast.makeText(this,"AUTO întrerupt. Verifică tabla.",Toast.LENGTH_LONG).show()
            }
        }
        if(!accepted) {
            controls?.visibility=View.VISIBLE
            setStatus("AUTO nu poate porni. Afișez soluția manual.")
            displayResult(id,result,fp)
        }
    }

    private fun capture(attempt:Int=0,id:Int) {
        if(stopped || id!=scanGeneration)return
        val img=reader?.acquireLatestImage()
        if(img==null) {
            if(attempt<8) handler.postDelayed({ capture(attempt+1,id) },180)
            else {
                scanBusy=false
                controls?.visibility=View.VISIBLE
                setStatus("Nu am primit captura. Încearcă din nou.")
                UserPrefs.saveDiagnosis(this,"Nu am primit captura de ecran.",0,0)
            }
            return
        }
        val bitmap=try { imageBitmap(img) } finally { img.close() }
        controls?.visibility=View.VISIBLE
        if(bitmap==null) {
            scanBusy=false
            setStatus("Nu pot citi captura ecranului.")
            UserPrefs.saveDiagnosis(this,"Nu pot citi captura ecranului.",0,0)
            return
        }
        setStatus("Scanez tabla...")
        Thread {
            val scan=try {
                Puzzle.detect(bitmap,UserPrefs.verifyResult(this) || UserPrefs.autoSolver(this),
                    UserPrefs.manualBoard(this,width,height))
            } catch(e:Exception) {
                Puzzle.Scan(null,"Eroare scanare: "+e.javaClass.simpleName)
            }
            val fp=try {
                scan.result?.let { boardFingerprint(bitmap,it.board,it.size) }
            } catch(_:Exception) { null }
            val alreadyPlaced=try {
                scan.result?.let { placedCatRows(bitmap,it) } ?: BooleanArray(0)
            } catch(_:Exception) { BooleanArray(0) }
            bitmap.recycle()
            handler.post {
                if(stopped || id!=scanGeneration)return@post
                scanBusy=false
                UserPrefs.saveDiagnosis(this,scan.message,scan.gridSize,scan.colors)
                if(scan.result==null) {
                    setStatus(scan.message)
                    appToast(scan.message,true)
                } else {
                    useSolution(id,scan.result,fp,alreadyPlaced)
                }
            }
        }.start()
    }
    override fun onDestroy() {
        stopped=true
        cancelAutoContinue()
        AutoTapService.cancel()
        stopCalibration(false)
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
}
