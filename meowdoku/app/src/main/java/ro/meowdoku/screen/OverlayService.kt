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
    private fun showControls() {
        val container=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(210,35,33,44))
            setPadding(8,8,8,8)
        }
        val button=Button(this).apply {
            text="🐱 MEOW"
            setOnClickListener { scan() }
        }
        val info=TextView(this).apply {
            text="Apasă pentru scanare automată"
            setTextColor(Color.WHITE)
            textSize=11f
        }
        container.addView(button)
        container.addView(info)
        statusText=info
        container.addView(Button(this).apply {
            text="ASCUNDE ★"
            setOnClickListener {
                // Invalidate any in-flight scan so it cannot redisplay old stars.
                scanGeneration++
                scanBusy=false
                clearMarks()
                statusText?.text="Steluțele au fost ascunse."
            }
        })
        container.addView(Button(this).apply {
            text="STOP"
            setOnClickListener {
                scanGeneration++
                scanBusy=false
                clearMarks()
                stopSelf()
            }
        })
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT
        ).apply { gravity=Gravity.TOP or Gravity.END; y=100 }
        controls=container
        wm.addView(container,params)
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
        statusText?.text="Scanez..."
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
                statusText?.text="Nu am primit captura. Încearcă din nou."
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
        if(bitmap==null) {
            scanBusy=false
            statusText?.text="Nu pot citi captura ecranului."
            return
        }
        statusText?.text="Scanez tabla..."
        Thread {
            val scan=try { Puzzle.detect(bitmap) }
                catch(e:Exception) { Puzzle.Scan(null,"Eroare la scanare: "+e.javaClass.simpleName) }
            bitmap.recycle()
            handler.post {
                if (stopped || id!=scanGeneration) return@post
                scanBusy=false
                val result=scan.result
                if(result==null) {
                    statusText?.text=scan.message
                    Toast.makeText(this,scan.message,Toast.LENGTH_LONG).show()
                    return@post
                }
                // Never stack windows: one puzzle => exactly N stars.
                clearMarks()
                val marks=MarksView(this,result.columns,result.board)
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
                    alpha=0.65f
                }
                overlay=marks
                wm.addView(marks,params)
                statusText?.text=scan.message+" — "+result.size+" steluțe"
                // Marks disappear automatically, even if user forgets to press Hide.
                val expiration=Runnable {
                    clearMarks()
                    statusText?.text="Steluțele au dispărut. Apasă MEOW pentru alt nivel."
                }
                clearJob=expiration
                handler.postDelayed(expiration,20000L)
                Toast.makeText(this,"Gata: "+result.size+"×"+result.size+" — marcajele dispar în 20 sec.",Toast.LENGTH_SHORT).show()
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
    private class MarksView(ctx:Context,val columns:IntArray,val board:Puzzle.Board):View(ctx) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.YELLOW
            style=Paint.Style.FILL
            textAlign=Paint.Align.CENTER
            setShadowLayer(4f,1f,2f,Color.BLACK)
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
