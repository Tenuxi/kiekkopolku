# Kiekkopolku Android — 0.2.0

Itsenäisesti Android Studiossa avattava Android-projekti. ApplicationId: `fi.kiekkopolku.app` (testiversio: `fi.kiekkopolku.app.debug`). Kotlin, Compose, Material 3, Room, Flow ja Hilt. MinSdk 26, compile/targetSdk 35. Integraatiokoodin tarkistus käyttää Metrixin dokumentoitua API:a. Koodi tallennetaan salattuna Android Keystoren avulla. Kierroshistorian tuonti ei vielä ole käytössä.

## Käynnistys

1. Avaa tämä kansio Android Studiossa tai asenna JDK 17 ja Android SDK 35/build-tools 35.0.0.
2. Määritä `ANDROID_HOME` tai paikallinen `local.properties` (`sdk.dir=...`). Tiedostoa ei tallenneta Gitiin.
3. `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Testit ja tarkistukset: `./gradlew :app:testDebugUnitTest :app:lintDebug`. Room- ja Compose-testit ajetaan Robolectricilla ilman emulaattoria. Ensimmäinen ajo lataa riippuvuudet. Skeemahistoria on `app/schemas`.

## Käyttö

Ensimmäinen käynnistys on tyhjä. Valitse **Tutustu esimerkkiperheeseen** tai lisää oma paikallinen pelaajaprofiili Pelaajat ja asetukset -näkymässä. Esimerkkidata on kuvitteellista ja merkitty näkyvästi. Profiilin voi lisätä ID:llä, integraatiokoodilla tai molemmilla. Nimi on valinnainen. Pelkkää ID:tä ei varmenneta verkosta; koodi tarkistetaan Metrixin kilpailulista-API:lla. Koodin omistajan ID:tä ei arvata.

Yläreunan pelaajavalinta vaikuttaa ratoihin, kierroksiin, aceihin ja tilastoihin. Ratalistassa on haku ja neljä järjestystä. Radan kautta pääsee pelaajan kierrokseen ja väylätuloksiin. Acen avaaminen vie samaan kierrokseen. Puuttuvat väylätiedot näytetään; niitä ei tulkita nollaksi aceksi. Yhteisen kierroksen pelaajakohtaiset osallistumiset lasketaan erikseen ja tapahtumien määrä kerrotaan erikseen.

Päivityspainike päivittää vain valittujen esimerkkiprofiilien paikalliset tiedot; oikeiden pelaajien kohdalla näytetään ettei integraatio ole käytössä. Synkronointiaika koskee onnistunutta esimerkkidatan tuontia. Esimerkkitiedot ja yksittäiset profiilit voi poistaa asetuksista. Tiedot säilyvät Roomissa uudelleenkäynnistyksessä. Teema on aina tumma ja harmaa.

## Rakenne ja rajaus

`domain` sisältää UI-mallit, laskennan, CourseMatcherin ja repository-rajapinnat. `data` sisältää Roomin, sisäisen tuontimallin, sample-adapterin ja transaktionaalisen repositoryn. `ui` sisältää ViewModelin ja Compose-näkymät. `di` kokoaa riippuvuudet.

Paikallisen MVP:n kolme repository-rajapintaa toteuttaa yksi luokka; erillistä verkko-, rata- tai tilastorepositoryn boilerplatea ei lisätty vielä. UI näkee vain domain-mallit. Metrix- ja metadatalähteiden rajapinnat on erotettu; Metrix-DTO:ita/endpointeja ei arvata. Koodin tarkistus käyttää OkHttpia ja Kotlin Serializationin JSON-jäsennystä. Kierros-DTO:t lisätään vasta sopimuksen varmistamisen jälkeen.

Navigaatio on pieni `rememberSaveable`-pohjainen tila ja Androidin takaisin-toiminto, ei erillistä navigaatiokehystä. Kartta/clustering kuuluu vaiheeseen 4. Hyväksymätöntä logoluonnosta ei ole muutettu lopulliseksi launcher-resurssiksi; tässä testiversiossa käytetään Androidin oletuskuvaketta.

## Versiointi

Version ainoa lähde on `version.properties`. Repositoryn juuressa `git config core.hooksPath .githooks` ottaa käyttöön commit-viestin version tarkistuksen. Viesti alkaa esimerkiksi `[v0.2.0]`. Version arkistointiohje on `../apk-releases/README.md`. Salaisia allekirjoitusavaimia ei tallenneta lähdekoodiin.

Version 0.2.0 tarkat integraatiorajat ja tallennusratkaisu: [julkaisumuistio](../docs/RELEASE-0.2.0.md). Room 1 → 2 -migraatio säilyttää version 0.1.0 historian.
