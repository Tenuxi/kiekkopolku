# PHASE 2 — Kiekkopolku 0.1.0

## Toteutettu

- Natiivi Kotlin/Compose/Material 3 -sovellus, Hilt ja Room.
- Profiilien lisäys, poisto, yksilölliset Metrix-ID:t ja pysyvä monivalinta.
- Vapaaehtoinen, poistettava esimerkkiperhe: 3 pelaajaa, 3 kuvitteellista rataa, 5 yhteistä kierrostapahtumaa, 13 pelaajan kierrosta ja 3 acea. Yhdeltä osallistumiselta puuttuu väylädata tarkoituksella.
- Ratojen haku ja järjestäminen nimen, kierrosmäärän, viimeisen ja ensimmäisen pelipäivän mukaan.
- Radan pelaajittaiset kierrokset, kierroslista, väylätulokset ja ace-korostus.
- Acet uusimmat ensin, pelaajakohtaiset määrät ja avaus kierrokseen.
- Yhteiset ja pelaajakohtaiset tilastot: uniikit fyysiset radat, osallistumiset, tapahtumat, acet ja päivämäärät.
- Roomin transaktiot, viiteavaimet ja unique-indeksit; yhteinen kierros ei monistu pelaajien määrän mukana.
- Järjestelmän vaalea/tumma teema, suomenkieliset tekstiresurssit, tyhjät ja virhetilat sekä paikallisen version ja esimerkkidatan selkeät merkinnät.
- Versionumeron lähdetiedosto, version tarkistava commit-hook, Gradle Wrapper, CI-tarkistukset ja muuttumaton APK-arkisto.

## Rajaus ja poikkeamat alkuperäisestä suunnitelmasta

PHASE 3:n oikeaa Metrix-integraatiota ei ole toteutettu. Profiilin lisääminen ei varmista ID:n olemassaoloa. Sovellus ei pyydä integraatiokoodia eikä käytä verkkoa. Päivityspainike palauttaa sample-datan valituille esimerkkipelaajille; oikeille profiileille se ilmoittaa integraation puuttumisesta.

Verkkokirjastot, Kotlin Serialization, WorkManager ja salaisuuksien tallennus lisätään vasta, kun niitä käytetään. MetrixDataSource- ja CourseMetadataDataSource-rajapinnat sisältävät integraation TODO:t. Sisäinen ImportBatch ei ole oletettu palvelun JSON-sopimus.

Yksi LocalHistoryRepository toteuttaa History-, Player- ja Sync-rajapinnat. Pienessä paikallisessa MVP:ssä ei vielä tarvita erillisiä Course- ja Stats-repositoryluokkia. Room-snapshot muunnetaan domain-malleiksi ja laskenta on testattavaa Kotlinia. Suurella oikealla historialla aggregaatit voidaan siirtää indeksoituihin DAO-kyselyihin.

Navigaatio käyttää tallentuvaa Compose-tilaa ja Androidin takaisin-painiketta. Kartta ja clustering kuuluvat vaiheeseen 4. Lopulliset launcher-, themed icon- ja splash-resurssit odottavat yhä logon hyväksyntää; testipaketissa on oletuskuvake.

## Varmennus

Käännös: `:app:assembleDebug`. Testit: `:app:testDebugUnitTest`. Staattinen Android-tarkistus: `:app:lintDebug`.

Lopullinen ajo 5.10.2026: **BUILD SUCCESSFUL**, 16 testiä läpi (5 domain-, 9 Room/repository- ja 2 Compose-testiä), lintissä 0 virhettä. Jäljellä 28 varoitusta: 27 lukittujen riippuvuuksien päivitysehdotusta ja yksi vanhempien Android-versioiden `fullBackupContent`-määritysehdotus. Vanhojen versioiden varmuuskopiointi on estetty `allowBackup=false`-asetuksella; Android 12+:lle on erilliset poissulkevat dataExtractionRules-säännöt. Riippuvuuksia ei päivitetty tarkistuksen jälkeen testaamattomaan yhdistelmään.

APK:n allekirjoitus varmistui, versionName on 0.1.0 ja versionCode 1. Jakelupaketin SHA-256 löytyy [arkistosta](../apk-releases/0.1.0/SHA256SUMS). Ratalistan renderöity näkymä tarkistettiin käyttöliittymätestin tuottamasta kuvasta.

Testit kattavat ratayhdistelyn, uniikkien ratojen laskennan, usean pelaajan/tyhjän valinnan, ace-säännöt, kaikki järjestykset, toistetun tuonnin, viiteavainten rollbackin, profiilin poiston, sample-datan poiston, epäonnistuvan päivityksen sekä tietokannan sulkemisen ja uudelleenavaamisen. Compose-testit kulkevat aloituksesta rataan ja väylätulokseen sekä uuden profiilin lisäämiseen ja valinnan tyhjentämiseen.

Robolectric-testit käyttävät Androidin simuloitua ajonaikaista ympäristöä. Fyysisellä puhelimella tai Android-emulaattorissa tehtävää asennus- ja käyttökokeilua ei ole tehty tässä työympäristössä. APK:n pakettitunniste, versio, SDK-vaatimukset ja allekirjoitus tarkistetaan Android SDK:n työkaluilla ennen arkistointia.

## Seuraava vaihe

PHASE 3 edellyttää PHASE 1:ssä kirjattujen historian saatavuuden ja käyttöoikeuksien selvittämistä. Paikallinen MVP ja APK-arkisto toimivat näistä riippumatta.
