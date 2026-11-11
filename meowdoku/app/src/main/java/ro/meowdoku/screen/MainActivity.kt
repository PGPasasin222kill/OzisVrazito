package ro.meowdoku.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity: Activity() {
    private val requestScreenCapture=10
    private var diagnosticsView:TextView?=null
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(32),dp(20),dp(35))
        }
        fun title(value:String,size:Float=16f):TextView = TextView(this).apply {
            text=value
            textSize=size
            setPadding(0,dp(14),0,dp(5))
        }
        panel.addView(title("🐱 Meowdoku Screen Solver",23f).apply { gravity=Gravity.CENTER })
        panel.addView(title("VERSIUNEA 1.0.1 • Setări complete",14f).apply { gravity=Gravity.CENTER })
        panel.addView(title("Scanare automată a oricărei grile detectabile. Butonul 🐱 este rotund și se poate trage.",14f).apply {
            gravity=Gravity.CENTER
        })
        panel.addView(Button(this).apply {
            text="Pornește peste joc"
            setOnClickListener { begin() }
        })
        panel.addView(Button(this).apply {
            text="ASCUNDE STELUȚELE ACUM"
            setOnClickListener { sendServiceCommand(OverlayService.ACTION_CLEAR) }
        })
        panel.addView(Button(this).apply {
            text="OPREȘTE COMPLET SOLVERUL"
            setOnClickListener { sendServiceCommand(OverlayService.ACTION_STOP) }
        })
        panel.addView(title("SETĂRI",20f))
        panel.addView(title("Detectare automată: ACTIVĂ. Nu trebuie aleasă dimensiunea.",14f))
        panel.addView(Button(this).apply {
            text="CALIBRARE DIRECT PE TABLĂ"
            setOnClickListener {
                sendServiceCommand(OverlayService.ACTION_CALIBRATE)
                moveTaskToBack(true)
            }
        })
        panel.addView(Button(this).apply {
            text="ȘTERGE CALIBRAREA (revino la detectarea automată)"
            setOnClickListener { UserPrefs.clearManualBoard(this@MainActivity) }
        })
        panel.addView(Switch(this).apply {
            text="Ascunde automat marcajele când se schimbă nivelul"
            isChecked=UserPrefs.autoHideLevelChange(this@MainActivity)
            setOnCheckedChangeListener { _,v -> UserPrefs.setAutoHideLevelChange(this@MainActivity,v) }
        })
        panel.addView(Switch(this).apply {
            text="Verifică tabla și soluția înainte să afișeze steluțe"
            textSize=15f
            isChecked=UserPrefs.verifyResult(this@MainActivity)
            setOnCheckedChangeListener { _,checked ->
                UserPrefs.setVerifyResult(this@MainActivity,checked)
            }
        })
        panel.addView(Switch(this).apply {
            text="Mesaje ale aplicației (oprit = mod silențios)"
            textSize=15f
            isChecked=UserPrefs.showMessages(this@MainActivity)
            setOnCheckedChangeListener { _,checked ->
                UserPrefs.setShowMessages(this@MainActivity,checked)
            }
        })
        val opacityLabel=title("Opacitatea steluțelor: ${UserPrefs.starOpacity(this@MainActivity)}%",16f)
        panel.addView(opacityLabel)
        panel.addView(SeekBar(this).apply {
            max=80
            progress=UserPrefs.starOpacity(this@MainActivity)-20
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek:SeekBar?,progress:Int,fromUser:Boolean) {
                    val value=20+progress
                    opacityLabel.text="Opacitatea steluțelor: $value%"
                    if(fromUser) UserPrefs.setStarOpacity(this@MainActivity,value)
                }
                override fun onStartTrackingTouch(seek:SeekBar?) {}
                override fun onStopTrackingTouch(seek:SeekBar?) {}
            })
        })
        panel.addView(title("Personalizarea marcajelor",18f))
        panel.addView(title("Forma marcajelor"))
        val shapes=listOf("Steluțe ★","Puncte ●","Cercuri ○","Pisicuțe 🐱")
        panel.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,shapes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(UserPrefs.markerShape(this@MainActivity))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) {
                    UserPrefs.setMarkerShape(this@MainActivity,position)
                }
                override fun onNothingSelected(p:AdapterView<*>?) {}
            }
        })
        panel.addView(title("Culoarea marcajelor"))
        val colors=listOf(Color.YELLOW,Color.GREEN,Color.RED,Color.CYAN,Color.WHITE,Color.MAGENTA)
        val colorNames=listOf("Galben","Verde","Roșu","Cyan","Alb","Mov")
        panel.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,colorNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(colors.indexOf(UserPrefs.markerColor(this@MainActivity)).coerceAtLeast(0))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) {
                    UserPrefs.setMarkerColor(this@MainActivity,colors[position])
                }
                override fun onNothingSelected(p:AdapterView<*>?) {}
            }
        })
        panel.addView(title("Durata marcajelor",16f))
        val choices=listOf(3,5,10,20,0)
        val labels=listOf("3 secunde","5 secunde","10 secunde","20 secunde","Până le ascund manual")
        panel.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(choices.indexOf(UserPrefs.duration(this@MainActivity)).coerceAtLeast(0))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent:AdapterView<*>?,v:View?,position:Int,id:Long) {
                    UserPrefs.setDuration(this@MainActivity,choices[position])
                }
                override fun onNothingSelected(parent:AdapterView<*>?) {}
            }
        })
        val sizeLabel=title("Mărimea butonului rotund: ${UserPrefs.sizeDp(this@MainActivity)} dp",16f)
        panel.addView(sizeLabel)
        panel.addView(SeekBar(this).apply {
            max=40
            progress=UserPrefs.sizeDp(this@MainActivity)-24
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek:SeekBar?,progress:Int,fromUser:Boolean) {
                    val value=24+progress
                    sizeLabel.text="Mărimea butonului rotund: $value dp"
                    if(fromUser) UserPrefs.setSizeDp(this@MainActivity,value)
                }
                override fun onStartTrackingTouch(seek:SeekBar?) {}
                override fun onStopTrackingTouch(seek:SeekBar?) {}
            })
        })
        panel.addView(title("Ține degetul pe 🐱 sau ⚙ și trage-le unde vrei pe ecran.",14f))
        panel.addView(Button(this).apply {
            text="Resetează poziția butoanelor"
            setOnClickListener { UserPrefs.saveOverlayPosition(this@MainActivity,-1,-1) }
        })
        panel.addView(title("Setările marcajelor se aplică la următoarea scanare. Mărimea butoanelor se aplică la următoarea pornire. ⚙ deschide ASCUNDE ★ și STOP.",13f))
        panel.addView(title("DIAGNOSTIC",20f))
        diagnosticsView=title(UserPrefs.diagnosis(this),14f)
        panel.addView(diagnosticsView)
        panel.addView(Button(this).apply {
            text="Actualizează diagnosticul"
            setOnClickListener { diagnosticsView?.text=UserPrefs.diagnosis(this@MainActivity) }
        })
        panel.addView(title("Notificare: SCANEAZĂ / ASCUNDE / OPREȘTE — funcționează când captura este activă.",14f))
        panel.addView(title("Plan viitor: auto-solver opțional cu acord explicit. Versiunea 1.0 nu apasă automat.",13f))
        panel.addView(title("Permisiunile de captură, notificarea obligatorie a serviciului și avertizările Samsung sunt generate de Android și nu pot fi dezactivate prin aplicație.",12f))
        setContentView(ScrollView(this).apply { addView(panel) })
    }

    override fun onResume() {
        super.onResume()
        diagnosticsView?.text=UserPrefs.diagnosis(this)
    }

    private fun sendServiceCommand(action:String) {
        // Works even when the floating controls are invisible or inaccessible.
        startService(Intent(this,OverlayService::class.java).setAction(action))
    }

    private fun begin() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            return
        }
        val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(),requestScreenCapture)
    }
    @Deprecated("Legacy activity result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==requestScreenCapture && resultCode==RESULT_OK && data!=null) {
            startForegroundService(Intent(this,OverlayService::class.java)
                .putExtra("resultCode",resultCode)
                .putExtra("resultData",data))
            moveTaskToBack(true)
        }
    }
}
