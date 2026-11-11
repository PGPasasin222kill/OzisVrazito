# CatPilot v1.1.0

**CatPilot** (fostul Meowdoku Screen Solver) este o aplicație Android cu iconiță de pisică violet și coroană, pentru asistarea jocului Meowdoku.

## Moduri de folosire

- **MANUAL (implicit):** apeși 🐱 MEOW, aplicația scanează tabla, verifică unicitatea soluției și afișează pozițiile. Nu apasă automat.
- **AUTO (opțional):** activezi switch-ul **AUTO solver** din aplicație și, separat, activezi manual **CatPilot** în **Setări Android → Accesibilitate → Aplicații instalate**. După aceea, intri în joc și apeși 🐱 MEOW (sau SCANEAZĂ din notificare). Dacă recunoaște o soluție validă, CatPilot trimite pe rând atingeri Android în centrele celulelor, prin AccessibilityService.
- AUTO **nu rulează de la sine** în fundal, iar permisiunea de accesibilitate nu poate fi activată automat. Ai mereu comenzi **ASCUNDE** și **OPREȘTE** în notificarea serviciului. AUTO este inițial dezactivat.

**Atenție:** AUTO funcționează cel mai sigur pe o tablă nouă, goală și complet vizibilă. Recunoașterea pisicilor deja plasate este experimentală; nu există garanție pentru niveluri cu marcaje existente sau ecran schimbat în timpul atingerilor. Dacă nivelul se mișcă sau vezi poziții greșite, oprește AUTO din setări și folosește modul manual.

## Funcții păstrate

- Scanare automată pentru grile detectabile între 3×3 și 24×24, indiferent de poziție; calibrare manuală cu două colțuri și salvare.
- Soluția este afișată **direct**, fără ecran de previzualizare.
- Setări pentru dimensiunea butonului rotund, poziționare prin tragere, opacitate, forma și culoarea marcajelor, durată de 3/5/10/20 secunde sau până la ascundere.
- Monitorizarea schimbării nivelului, verificări de regiuni/soluție și diagnostic local.
- Acțiunile SCANEAZĂ, ASCUNDE și OPREȘTE în notificarea Android.
- Oprire de urgență din fereastra principală, din meniul flotant și din notificare.
- Avertizările de compatibilitate Samsung și permisiunile sistemului nu pot fi oprite din aplicație.

## Instalare pe Samsung

1. GitHub → Actions → ultima rulare reușită → **Artifacts** → **Meowdoku-Screen-Solver-APK** → extrage `app-debug.apk`.
2. Instalează și deschide **CatPilot**. Pachetul Android rămâne `ro.meowdoku.screen`, deci instalarea poate păstra setările vechi dacă semnătura APK este aceeași.
3. Pentru AUTO, activează întâi serviciul Android de accesibilitate **CatPilot**. Pe unele versiuni Android, aplicațiile instalate din APK au „Setări restricționate”: în informațiile aplicației, meniul cu trei puncte poate permite „Permite setări restricționate”, dacă sistemul oferă opțiunea.
4. În CatPilot, pornește captura ecranului și intră în joc. Alege MANUAL sau AUTO din setări.

Aplicația nu necesită cont sau internet pentru scanare și nu înregistrează conținutul ferestrelor de accesibilitate.

> Funcția AUTO trimite atingeri numai după ce utilizatorul a activat explicit AUTO și a cerut o scanare. Recomandăm verificarea pe ecranul telefonului înainte de a considera rezultatul stabil.
