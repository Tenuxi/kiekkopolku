# Kiekkopolku Android — 0.5.1

Itsenäisesti Android Studiossa avattava Android-projekti. ApplicationId: `fi.kiekkopolku.app` (testiversio: `fi.kiekkopolku.app.debug`). Kotlin, Compose, Material 3, Room, Flow ja Hilt. MinSdk 26, compile/targetSdk 35. Integraatiokoodin tarkistus käyttää Metrixin dokumentoitua API:a. Koodi tallennetaan salattuna Android Keystoren avulla. Oikean kierroshistorian tuonti käyttää `my_competitions`- ja `result`-kutsuja.

## Käynnistys

1. Avaa tämä kansio Android Studiossa tai asenna JDK 17 ja Android SDK 35/build-tools 35.0.0.
2. Määritä `ANDROID_HOME` tai paikallinen `local.properties` (`sdk.dir=...`). Tiedostoa ei tallenneta Gitiin.
3. `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Testit ja tarkistukset: `./gradlew :app:testDebugUnitTest :app:lintDebug`. Room- ja Compose-testit ajetaan Robolectricilla ilman emulaattoria. Ensimmäinen ajo lataa riippuvuudet. Skeemahistoria on `app/schemas`.

## Käyttö

Ensimmäinen käynnistys on tyhjä. Lisää oma pelaajaprofiili Pelaajat ja asetukset -näkymässä. Omaa historiaa varten anna sekä Metrix-ID että integraatiokoodi. **Tutustu esimerkkiperheeseen** on vapaaehtoinen kokeilu. Esimerkkidata on kuvitteellista ja merkitty näkyvästi. Profiilin voi lisätä ID:llä, integraatiokoodilla tai molemmilla. Nimi on valinnainen. Pelkkää ID:tä ei varmenneta verkosta; koodi tarkistetaan Metrixin kilpailulista-API:lla. Koodin omistajan ID:tä ei arvata.

Yläreunan pelaajavalinta vaikuttaa ratoihin, kierroksiin, aceihin ja tilastoihin. Ratalistassa on haku ja neljä järjestystä. Radan kautta pääsee pelaajan kierrokseen ja väylätuloksiin. Acen avaaminen vie samaan kierrokseen. Puuttuvat väylätiedot näytetään; niitä ei tulkita nollaksi aceksi. Yhteisen kierroksen pelaajakohtaiset osallistumiset lasketaan erikseen ja tapahtumien määrä kerrotaan erikseen.

Sovelluksen avautuminen käynnistää tallennettujen oikeiden pelaajien taustapäivityksen (myös suodatuksessa piilotetut). Nopeat paluut alle minuutin sisällä eivät käynnistä uutta hakua. Päivityspainike tarkistaa valittujen pelaajien koko saatavilla olevan historian uudelleen. Latausilmaisin korvaa päivityskuvakkeen ja eteneminen näkyy yläpalkissa; historiaa voi selata ja pelaajavalintaa vaihtaa haun aikana. Pelaajakohtainen virhe ja viimeisin onnistunut tarkistus näkyvät asetuksissa. Esimerkkitiedot ja yksittäiset profiilit voi poistaa asetuksista. Tiedot säilyvät Roomissa uudelleenkäynnistyksessä. Teema on aina tumma ja harmaa.

## Rakenne ja rajaus

`domain` sisältää UI-mallit, laskennan, CourseMatcherin ja repository-rajapinnat. `data` sisältää Roomin, sisäisen tuontimallin, sample-adapterin ja transaktionaalisen repositoryn. `ui` sisältää ViewModelin ja Compose-näkymät. `di` kokoaa riippuvuudet.

Paikallisen MVP:n kolme repository-rajapintaa toteuttaa yksi luokka; verkkosopimus on erillisessä MetrixApi-rajapinnassa ja tulosten muunnos MetrixImport-tiedostossa. UI näkee vain domain-mallit. Metrix- ja metadatalähteiden rajapinnat on erotettu; Metrix-tuonti perustuu dokumentaatioon sekä julkisista oikeista tuloksista tarkistettuihin rakenteisiin. OkHttp hoitaa HTTPS-pyynnöt ja Kotlin Serialization JSON-jäsennyksen. Vain tallennetun pelaaja-ID:n tulokset kirjoitetaan Roomiin. Integraatiokoodin omistaja-ID:tä ei arvata eikä selainprofiilia kaavita.

Navigaatio on pieni `rememberSaveable`-pohjainen tila ja Androidin takaisin-toiminto, ei erillistä navigaatiokehystä. Kartta käyttää MapLibre Native 11.13.5:tä ja OpenFreeMapin vaaleaa Positron-tyyliä. Lähekkäiset ratamerkit ryhmitellään ja merkin kautta voi avata radan historian. Kartta ei tarvitse API-avainta tai laitteen sijaintilupaa. Sovelluksella on oma harmaa karttapinni–kori-kuvake, adaptive- ja monochrome-resurssit sekä splash-logo.

## Versiointi

Version ainoa lähde on `version.properties`. Repositoryn juuressa `git config core.hooksPath .githooks` ottaa käyttöön commit-viestin version tarkistuksen. Viesti alkaa esimerkiksi `[v0.3.0]`. Version arkistointiohje on `../apk-releases/README.md`. Salaisia allekirjoitusavaimia ei tallenneta lähdekoodiin.

Version 0.3.0 tarkat integraatiorajat ja tallennusratkaisu: [julkaisumuistio](../docs/RELEASE-0.3.0.md). Room 1 → 2 -migraatio säilyttää version 0.1.0 historian.

Automaattipäivitys käyttää alle vuorokauden vanhaa paikallista kopiota yli 30 päivää vanhoista valmiista kierroksista. Uudet, tuoreet ja keskeneräiset haetaan uudelleen. Manuaalinen päivitys ohittaa tämän välimuistin. Palvelun kieltämä historia tai tuntematon vastaus näytetään osittaisena tuontina. Tunnettu paripelityyppi ohitetaan; joukkueen tulosta ei kirjata henkilökohtaiseksi aceksi. Rataluvussa Metrixin ParentID yhdistää tunnetut layoutit fyysiseksi ratakohteeksi. Ilman parent-tietoa layout säilyy omana kohteenaan. Ratalistan oletusjärjestys on viimeksi pelattu.

## Versio 0.4.0

Room 2 → 3 lisää Metrix-tapahtumien pysyvän indeksin säilyttäen kierrokset ja profiilit. Tapahtumatunnisteiden määrä erotetaan varmennetuista kierroksista: kaikki ID:t eivät ole pelattuja kierroksia. Koko tallennettu historia ja viimeisten 12 kuukauden tulokset näytetään erikseen. Rajoitettuja vanhoja tuloshakuja ei tehdä automaattisesti uudelleen vuorokauden sisällä; manuaalinen päivitys yrittää aina uudelleen.

Ratojen `course`-kutsusta tuodaan Lat/Lng, kaupunki, maa ja ParentID. Tallennetut ratatiedot päivitetään myös silloin, kun vanha tuloskortti on estetty. Koordinaatit säilyvät myöhemmissä tuloshaussa. Metatiedon automaattinen välimuisti on seitsemän päivää; manuaalinen päivitys ohittaa sen. Puuttuvia sijainteja ei arvata.

Karttapohja vaatii verkkoyhteyden tai SDK:n aiemman välimuistin; koko Suomen offline-karttaa ei luvata. Tiedossa olevat radat, koordinaatit ja historia säilyvät laitteessa. Karttapalvelu saa karttaruutupyynnöt; pelaajien nimiä, tuloksia ja Metrix-koodeja ei lähetetä sille. Tekijät: OpenFreeMap, OpenMapTiles ja OpenStreetMap.

[Version 0.4.0 julkaisumuistio](../docs/RELEASE-0.4.0.md).

## Versio 0.5.0 ja allekirjoitus

Vanhan API-tuloskortin estyessä sovellus yrittää julkista tapahtumasivua ilman integraatiokoodia tai kirjautumista. Vain täsmälleen vastaavan pelaajan varmennettu pelattu käynti, radan ID/nimi ja päivä tallennetaan. Rekisteröitymiset, epäselvät sivut ja koontitapahtumat eivät muutu automaattisesti kierroksiksi. Sivurakenteen muuttuessa tietoja voi jäädä puuttumaan. Tuloskortit haetaan edelleen vain API:sta. Varmennetut käynnit tallentuvat Roomiin; nimet ja koordinaatit täydennetään course-API:sta. Puuttuvia koordinaatteja ei arvata.

Release käyttää paikallista `.signing/release.properties`-tiedostoa (storeFile, storePassword, keyAlias, keyPassword) ja sen osoittamaa keystorea. Molemmat on suljettu Gitistä. Julkaisukoneen avain on `.signing/release.jks`. **Varmuuskopioi koko .signing-kansio turvallisesti:** sama avain tarvitaan tuleviin päivityksiin. Kloonattu projekti ei rakenna release-pakettia ilman allekirjoitusasetuksia. Rakenna `./gradlew :app:assembleRelease` ja tarkista `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`.

Release (`fi.kiekkopolku.app`) ja debug (`fi.kiekkopolku.app.debug`) ovat erillisiä asennuksia ja tietovarastoja. Nykyisen testiversion päivitys tehdään debug-APK:lla alkuperäisellä debug-avaimella. Release tarvitsee profiilien lisäyksen uudelleen. Allekirjoitus todentaa paketin ja mahdollistaa päivitykset; se ei takaa Play Protect -varoitusten poistumista.

## Versio 0.5.1

Kartta täyttää Scaffoldin vapaaksi jättämän alueen ilman pysyviä tilasto-, pelaajavalinta- tai seliterivejä. Kartan tiedot avautuvat info-painikkeesta; pelaajavalinta yläpalkin suodattimesta. Karttamerkit ja klusterit toimivat ennallaan. Insets-kulutus estää järjestelmäpalkkien ja näppäimistön varauksen moninkertaistumisen; kartalle siirtyminen sulkee näppäimistön.

Odotettu Metrix-historiarajoitus tallentuu LIMITED-tilana. Se ei yksin aiheuta automaattipäivityksen virheilmoitusta, mutta selitys pysyy pelaajan asetuksissa ja manuaalinen päivitys kertoo rajoituksesta lyhyesti. Oikeat virheet eivät enää peity myöhempien vuosirajoitusten alle. Virheilmoitus on lyhyt, tilapäinen snackbar, jonka Asetukset-painike avaa pelaajakohtaiset syyt.
