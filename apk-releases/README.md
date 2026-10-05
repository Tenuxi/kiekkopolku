# Kiekkopolun APK-arkisto

Jokainen julkaistu testiversio säilytetään omassa versionumerokansiossaan. Vanhaa APK:ta ei korvata. Tarkka lähdekoodi löytyy saman version Git-tagista, esimerkiksi `v0.1.0`.

| Versio | APK | Sisältö |
|---|---|---|
| 0.4.0 | [kiekkopolku-0.4.0-debug.apk](0.4.0/kiekkopolku-0.4.0-debug.apk) | Kartta, ratakoordinaatit, koko historia ja viimeisen vuoden tilastot |
| 0.3.0 | [kiekkopolku-0.3.0-debug.apk](0.3.0/kiekkopolku-0.3.0-debug.apk) | Metrix-historian tuonti, automaattipäivitys ja latausilmaisin |
| 0.2.0 | [kiekkopolku-0.2.0-debug.apk](0.2.0/kiekkopolku-0.2.0-debug.apk) | Tumma harmaa teema ja ID-/integraatiokoodilisäys |
| 0.1.0 | [kiekkopolku-0.1.0-debug.apk](0.1.0/kiekkopolku-0.1.0-debug.apk) | Paikallinen MVP ja vapaaehtoinen esimerkkiperhe |

GitHubissa avaa APK-tiedosto ja valitse **Download raw file**, tai käytä projektin README:n suoraa latauslinkkiä. Puhelin saattaa pyytää sallimaan asennuksen käytetystä selaimesta. Vähimmäisversio on Android 8.0 (API 26).

Tarkistussumma on version `SHA256SUMS`-tiedostossa. Linuxissa: `sha256sum -c SHA256SUMS` samassa kansiossa APK:n kanssa.

## Vanhan version testaaminen

Android yleensä estää pienemmän versionCode-arvon asentamisen uudemman päälle. Helpoin tapa on käyttää erillistä testilaitetta/emulaattoria tai poistaa nykyinen testisovellus ennen vanhan asentamista. **Poistaminen hävittää sovelluksen paikallisen datan.** Tietokannan downgradea ei tueta. Debug-version applicationId on `fi.kiekkopolku.app.debug`, jotta se voi myöhemmin olla varsinaisen julkaisuversion rinnalla.

APK:t ovat kehityksen debug-avaimella allekirjoitettuja testipaketteja. Sama allekirjoitusavain tarvitaan päivittämiseen ilman poistoa. Avain ei kuulu Gitiin; eri koneella/CI:ssä luotu debug-APK voi vaatia vanhan sovelluksen poistamisen. Julkisen tuotantoversion allekirjoitus ratkaistaan erikseen ennen sen julkaisua.

## Uuden version arkistointi

1. Päivitä `kiekkopolku-android/version.properties`: versionName ja aina kasvava versionCode.
2. Aja `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` Android-kansiossa.
3. Aja repositoryn juuressa `python3 scripts/archive-apk.py`.
4. Lisää versiokohtainen muutoskuvaus ja päivitä lataustaulukko.
5. Commit muodossa `[v0.1.0] Kuvaus` käyttäen senhetkistä versiota; luo vastaava `v0.1.0`-tagi ja pushaa branch sekä tagi.

APK-arkisto kasvattaa Git-historiaa. Testipaketteja säilytetään tässä pyynnön mukaisesti; määrän kasvaessa voidaan siirtyä GitHub Releases -tiedostoihin säilyttäen indeksi ja tarkistussummat Gitissä.
