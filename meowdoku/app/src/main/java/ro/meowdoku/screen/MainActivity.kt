package ro.meowdoku.screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class MainActivity: Activity() {
    private val code = 10
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(30,50,30,30)
            gravity=Gravity.CENTER
        }
        layout.addView(TextView(this).apply {
            text="Meowdoku Screen Solver\n\nButon plutitor MEOW • dimensiune detectată automat\nScanează tabla și arată stelele peste joc.\nNu apasă automat."
            textSize=20f
            gravity=Gravity.CENTER
        })
        layout.addView(Button(this).apply {
            text="Pornește MEOW"
            setOnClickListener { begin() }
        })
        layout.addView(TextView(this).apply {
            text="Permite afișarea peste alte aplicații și partajarea ecranului.\nDupă pornire, intră în joc și apasă MEOW.\nRecunoașterea este experimentală: identifică automat tabla, rândurile, coloanele și culorile."
            gravity=Gravity.CENTER
        })
        setContentView(layout)
    }
    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(),code)
    }
    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==code && resultCode==RESULT_OK && data!=null) {
            val intent=Intent(this,OverlayService::class.java)
                .putExtra("resultCode",resultCode)
                .putExtra("resultData",data)
            startForegroundService(intent)
            moveTaskToBack(true)
        }
    }
}
