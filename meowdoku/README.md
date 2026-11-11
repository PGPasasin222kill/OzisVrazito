# Meowdoku Screen Solver v1.0.1

Ajutor vizual Android pentru Meowdoku. Folosește captură de ecran numai cu acordul utilizatorului, recunoaște automat dimensiunea grilei și indică pozițiile pisicilor. **Nu apasă automat pe joc.**

## Folosire
1. Deschide aplicația, configurează opțiunile și apasă **Pornește peste joc**. Acordă permisiunile Android pentru suprapunere și captură.
2. În joc, apasă butonul rotund 🐱. Acesta dispare cât timp se face captura, pentru a nu acoperi culorile.
3. După scanare și verificarea automată a soluției, marcajele apar imediat pe tablă. Nu mai există ecran de previzualizare sau confirmare.
4. Pentru o tablă nedetectată corect: deschide ⚙ → **CALIBREAZĂ TABLA**. Mută colțurile galbene 1 și 2 peste colțurile grilei, apoi **SALVEAZĂ** și scanează din nou. Calibrarea persistă, dar o poți reseta din setările aplicației.
5. Accesează toate opțiunile în aplicația principală sau prin ⚙ → **SETĂRI COMPLETE**. Diagnosticul ultimei scanări afișează dimensiunea, numărul culorilor și mesajul de eroare.
6. Notificarea Android are **SCANEAZĂ / ASCUNDE / OPREȘTE** când serviciul de captură rulează. Notificarea obligatorie nu poate fi dezactivată din aplicație.

## Funcții de siguranță
- Un singur set de marcaje pentru o soluție verificată; toate ferestrele temporare se închid la oprire.
- Comenzi **ASCUNDE STELUȚELE ACUM** și **OPREȘTE COMPLET SOLVERUL** în aplicația principală și în notificare.
- Timp marcaje: 3/5/10/20 secunde sau manual; marcajele apar imediat după scanare.
- Monitorizare locală a schimbării culorilor tablei după scanare; după două eșantioane diferite, marcajele sunt ascunse. Pot apărea fals pozitive la animații.
- Solverul acceptă grile detectabile între 3×3 și 24×24. Detectarea automată nu este garantată pentru orice temă, rezoluție sau regiune acoperită.

## Setări
- Verificarea zonelor de culoare, a conectivității regiunilor și a unicității soluției.
- Afișare directă a soluției după verificarea unicității; fără confirmare suplimentară.
- Forma marcajelor: stea, punct, inel, pisicuță; culori galben, verde, roșu, cyan, alb, mov.
- Opacitate desen 20–100%; fereastra plutitoare are limitare Android pentru trecerea atingerilor.
- Mărime buton rotund 24–64 dp; mutare prin tragere, poziție salvată.
- Mod silențios pentru mesajele produse de aplicație. Dialogul de aprobare a capturii și mesajele Samsung vin de la sistem și nu pot fi suprimate.

## APK
GitHub → Actions → ultima rulare reușită **Build Meowdoku Android APK** → **Artifacts** → **Meowdoku-Screen-Solver-APK** → extrage **app-debug.apk**.

APK-ul din Actions este o variantă de test. Verifică pe Samsung că în aplicație apare **Versiunea 1.0.1**. Dacă Android refuză actualizarea din cauza semnăturilor debug diferite, dezinstalează vechea versiune înainte de instalare; acest lucru poate șterge setările locale.

## Plan ulterior
**Auto-solver opțional** care ar putea introduce pisicile automat doar după permisiune explicită a utilizatorului, separat de modul vizual. Nu este implementat în v1.0.
