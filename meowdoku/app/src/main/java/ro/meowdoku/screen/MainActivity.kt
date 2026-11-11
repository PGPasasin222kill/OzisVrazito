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
import android.widget.ImageView
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity: Activity() {
    private val requestScreenCapture=10
    private var diagnosticsView:TextView?=null
    private var autoServiceView:TextView?=null
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
        panel.addView(ImageView(this).apply {
            setImageResource(R.drawable.catpilot_logo)
            contentDescription="CatPilot — pisică violet cu coroană"
            layoutParams=LinearLayout.LayoutParams(dp(100),dp(100)).apply {
                gravity=Gravity.CENTER_HORIZONTAL
                bottomMargin=dp(4)
            }
        })
        panel.addView(title("🐱 CatPilot",26f).apply { gravity=Gravity.CENTER })
        panel.addView(title("VERSIUNEA 1.2.0 • Setări complete",14f).apply { gravity=Gravity.CENTER })
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
        panel.addView(title("AUTO SOLVER",20f))
        panel.addView(Switch(this).apply {
            text="AUTO: apasă singur pisicile după scanarea MEOW"
            textSize=16f
            isChecked=UserPrefs.autoSolver(this@MainActivity)
            setOnCheckedChangeListener { _,enabled ->
                UserPrefs.setAutoSolver(this@MainActivity,enabled)
                if(enabled && !AutoTapService.isReady()) {
                    Toast.makeText(this@MainActivity,
                        "Activează CatPilot în Setări → Accesibilitate → Aplicații instalate.",
                        Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                autoServiceView?.text=autoStatus()
            }
        })
        panel.addView(Switch(this).apply {
            text="Continuă automat după nivel (Leaderboard / Flawless)"
            textSize=16f
            isChecked=UserPrefs.autoContinue(this@MainActivity)
            setOnCheckedChangeListener { _,yes ->
                UserPrefs.setAutoContinue(this@MainActivity,yes)
            }
        })
        panel.addView(Switch(this).apply {
            text="Mod sigur: nu apăsa dacă ecranul nu este recunoscut"
            textSize=15f
            isChecked=UserPrefs.safeAuto(this@MainActivity)
            setOnCheckedChangeListener { _,yes -> UserPrefs.setSafeAuto(this@MainActivity,yes) }
        })
        panel.addView(title("Pauză după închiderea ecranului de final"))
        val delays=listOf(800,1100,1500,2000)
        val delayLabels=listOf("0,8 secunde","1,1 secunde","1,5 secunde","2 secunde")
        panel.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_item,delayLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val current=UserPrefs.continueDelayMs(this@MainActivity)
            setSelection(delays.indices.minByOrNull { kotlin.math.abs(delays[it]-current) } ?: 1)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) {
                    UserPrefs.setContinueDelayMs(this@MainActivity,delays[position])
                }
                override fun onNothingSelected(p:AdapterView<*>?) {}
            }
        })
        panel.addView(title("Număr maxim de încercări la fiecare nivel"))
        val retries=listOf(3,5,8)
        panel.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_item,listOf("3 încercări","5 încercări","8 încercări")).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(retries.indexOf(UserPrefs.autoRetryLimit(this@MainActivity)).coerceAtLeast(0))
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) {
                    UserPrefs.setAutoRetryLimit(this@MainActivity,retries[position])
                }
                override fun onNothingSelected(p:AdapterView<*>?) {}
            }
        })
        panel.addView(title(
            "AUTO-continuare pornește doar după ce ai declanșat MEOW cu AUTO activ. " +
            "Detectează ecranele de final, apasă butonul potrivit și caută nivelul următor. " +
            "Dacă nu recunoaște ecranul sau nu poate scana, se oprește fără atingeri la întâmplare.",
            13f))
        autoServiceView=title(autoStatus(),14f)
        panel.addView(autoServiceView)
        panel.addView(Button(this).apply {
            text="ACTIVARE SERVICIU DE ACCESIBILITATE"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        panel.addView(title(
            "AUTO este opțional și inițial oprit. Necesită activarea manuală a accesibilității Android. " +
            "Pe tabla goală funcționează cel mai sigur. Dacă există deja pisici, verifică rezultatul: " +
            "recunoașterea pieselor existente este experimentală. Comenzile STOP și ASCUNDE anulează secvența de apăsări.",
            13f))
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
        panel.addView(title("Mod MANUAL: numai marcaje. Mod AUTO: gesturi la cererea ta, după verificarea soluției.",13f))
        panel.addView(title("Permisiunile de captură, notificarea obligatorie a serviciului și avertizările Samsung sunt generate de Android și nu pot fi dezactivate prin aplicație.",12f))
        setContentView(ScrollView(this).apply { addView(panel) })
    }

    private fun autoStatus():String = when {
        AutoTapService.isReady() && UserPrefs.autoSolver(this) ->
            "AUTO gata: serviciul Android este activ."
        AutoTapService.isReady() -> "Serviciul Android este activ; AUTO oprit."
        else -> "Serviciul de accesibilitate nu este activ. AUTO nu poate apăsa."
    }

    override fun onResume() {
        super.onResume()
        diagnosticsView?.text=UserPrefs.diagnosis(this)
        autoServiceView?.text=autoStatus()
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
