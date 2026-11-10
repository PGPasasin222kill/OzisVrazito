package ro.meowdoku.screen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity: Activity() {
    private val code = 10
    private fun dp(value:Int) = (value*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(22),dp(40),dp(22),dp(24))
        }
        panel.addView(TextView(this).apply {
            text="🐱 Meowdoku Screen Solver"
            textSize=24f
            gravity=Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            text="Scanează tabla și arată pisicile direct peste joc."
            textSize=15f
            setPadding(0,dp(14),0,dp(18))
            gravity=Gravity.CENTER
        })
        panel.addView(Button(this).apply {
            text="Pornește MEOW peste joc"
            setOnClickListener { begin() }
        })
        panel.addView(TextView(this).apply {
            text="Setări"
            textSize=22f
            setPadding(0,dp(26),0,dp(10))
        })
        panel.addView(Switch(this).apply {
            text="Afișează mesaje (sub MEOW și notificări scurte)"
            textSize=15f
            isChecked=UserPrefs.showMessages(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                UserPrefs.setShowMessages(this@MainActivity,enabled)
            }
        })
        val sizeText=TextView(this).apply {
            textSize=16f
            setPadding(0,dp(22),0,dp(4))
            text="Mărimea butonului: ${UserPrefs.sizeDp(this@MainActivity)} dp"
        }
        panel.addView(sizeText)
        panel.addView(SeekBar(this).apply {
            max=28 // 36..64dp; changes to the floating buttons apply next time they start
            progress=UserPrefs.sizeDp(this@MainActivity)-36
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?, value:Int, fromUser:Boolean) {
                    val size=36+value
                    sizeText.text="Mărimea butonului: $size dp"
                    if(fromUser) UserPrefs.setSizeDp(this@MainActivity,size)
                }
                override fun onStartTrackingTouch(bar:SeekBar?) {}
                override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        panel.addView(TextView(this).apply {
            text="În joc: 🐱 scanează, ⚙ deschide meniul, ASCUNDE ★ șterge marcajele. Modificările de mărime se aplică la următoarea pornire."
            textSize=14f
            setPadding(0,dp(12),0,dp(15))
        })
        panel.addView(TextView(this).apply {
            text="Notă: mesajele Android/Samsung despre permisiuni sau compatibilitate sunt generate de sistem și nu pot fi dezactivate din aceste setări. Mesajele aplicației pot fi ascunse."
            textSize=13f
        })
        setContentView(ScrollView(this).apply { addView(panel) })
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
