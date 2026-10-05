# Kiekkopolku 0.5.0

## Kartta ja vanha historia

Metrixin `my_competitions` palauttaa tapahtumatunnisteita, ei jokaiselle tunnisteelle rataa tai päivää. Yli vuoden vanhan `result`-haun estyessä `Competition` voi olla null. Tunnisteesta ei voi laskea Course ID:tä. Tapahtumien ja estettyjen tuloshakujen määrät ovat käyttäjän datasta laskettuja, eivät sovellukseen kirjoitettuja vakioita.

Sovellus yrittää tällöin julkisen tapahtumasivun perustietoja ilman kirjautumista tai integraatiokoodia. Konservatiivinen jäsennin edellyttää täsmäävää tapahtumatunnistetta, yhtä rataviitettä ja kelvollista vanhaa päivää sekä täsmälleen tallennetun pelaajan tulosriviä, jossa pelattujen väylien merkintä vahvistaa pelaamisen. Pelkkä ilmoittautuminen, toisen pelaajan tulos tai epäselvä sivu ei riitä. Tuloskortteja tai väyläpisteitä ei kopioida sivulta.

Varmennetulle käynnille tallennetaan `METADATA_ONLY`-tila olemassa olevaan Room-malliin: sama tapahtuma/pelaaja-avain estää duplikaatit, eikä olemassa olevaa tuloskorttia korvata metatiedolla. Jos API myöhemmin antaa tuloskortin, sama käynti täydentyy. Metrixin course-API täydentää nimen ja Lat/Lng-koordinaatit; tarvittaessa sijainti tulee ParentID:n osoittamalta radalta. Paikallinen ratavälimuisti ja käynnit säilyvät. Automaattinen tulosrajoituksen uusintatarkistus odottaa vuorokauden; jo löydettyä vanhaa julkista käyntiä ei haeta joka avauksella uudelleen. Metadatan epäonnistuminen ei estä muiden tapahtumien käsittelyä.

Kaikkien historiallisten tapahtumien löytymistä ei luvata: yksityiset sivut, koontitapahtumat, paripelit ja muuttunut HTML voivat jäädä ratkaisematta. Käyttöliittymä kertoo löydettyjen vanhojen käyntien ja selvittämättömien rajoitettujen tapahtumien määrät. Tapahtumatunnisteiden määrää ei esitetä kierrosten määränä. Luvut kuvaavat valittuja pelaajia; kierrokset lasketaan pelaajakohtaisesti.

Kartta käyttää vaaleaa OpenFreeMap Positron -tyyliä, mutta sovelluksen muu ulkoasu säilyy hiilenharmaana. Paikallisesti piirretyssä pisarapinnissä on yksinkertainen kori:

- Vihreä: vähintään yksi viimeisten 12 kuukauden pelattu käynti, josta on API-tulostietoja.
- Oranssi: vain vanhempia käyntejä. Infopaneeli kertoo tuloskorttien puuttumisesta; jo aiemmin välimuistiin saatuja vanhoja tuloksia ei väitetä puuttuviksi.
- Harmaa: esimerkiksi tulevaisuuteen päivätty tai muuten epäselvä aineisto.

Ryhmiteltyjen merkkien numero kertoo kohteiden määrän. Pinneistä avautuvat radan nimi, kierrosmäärä, viimeisin pelipäivä ja pääsy historiaan. Puuttuvat sijainnit ovat edelleen erillisessä listassa. Karttapalvelulle ei lähetetä pelaajatietoja, tuloksia eikä Metrix-koodeja.

## Kuvake ja asennus

Oma harmaa karttapinni–kori-symboli on SVG-lähteenä sekä Androidin adaptive foreground/background- ja monochrome-resursseina. Mukana ovat viisi PNG-mipmap-tiheyttä ja splash-logo Android 8:sta lähtien. Kuvakkeessa ei ole tekstiä. Alkuperäiset vihreät rasteriluonnokset ovat edelleen luonnosarkistossa.

Molemmat APK:t ovat version 0.5.0 / versionCode 5:

- **Debug** päivittää nykyisen `fi.kiekkopolku.app.debug`-asennuksen samalla allekirjoitusavaimella. Käytä tätä säilyttääksesi nykyisen testisovelluksen paikalliset tiedot.
- **Release** on erillinen `fi.kiekkopolku.app`-asennus, allekirjoitettu omalla RSA-3072-julkaisuavaimella. Lisää pelaajaprofiilit uudelleen. APK Signature Scheme v2 -tarkistus läpäisee Androidin minSdk 26 -vaatimuksen.

Julkaisuavaimen SHA-256-varmennejälki: `a9c2a92b40b12fbd84318cd185cd5b445c7cba3dc35ded718b4e951b96e9e02d`.
Debug-avaimen SHA-256-varmennejälki: `ed3447ca65cc0609705aea12962b6eb493bf7f8f03c52a2eb9711cec2a726dab`.

Yksityinen avain ja salasanat ovat vain julkaisukoneen Gitistä poissuljetussa `kiekkopolku-android/.signing`-kansiossa. Kansio on varmuuskopioitava turvallisesti myöhempiä päivityksiä varten. Julkaisuavainta ei pidä korvata uudella. Allekirjoitus ei takaa Play Protect -varoitusten poistumista: myös oikein allekirjoitettu Play-kaupan ulkopuolinen sovellus voidaan tarkistaa tai merkitä tuntemattomaksi.

## Varmennus

- 56 yksikkö-, Room-, integraatio- ja Compose-testiä: hyväksytty. Mukana julkinen vanhan tapahtuman havaintotesti sekä synteettiset väärän pelaajan, puuttuvan radan, virhepäivän, ilmoittautumisen, duplikaatin, vuosirajan ja tuloskortiksi täydentymisen tapaukset.
- Debug- ja release-lint: 0 virhettä (debug 36, release 27 varoitusta; muun muassa riippuvuuspäivitykset ja resurssisuositukset). Molemmat APK-käännökset läpäisivät tarkistuksen.
- APK-allekirjoitukset ja debug-päivityksen sama varmenne tarkistetaan `apksigner`-työkalulla. Pakettien SHA-256-tarkistussummat ovat julkaisuarkistossa.
- Käyttäjän koko Metrix-historiaa ei ajettu tässä ympäristössä eikä yksityistä ID:tä/koodia tallennettu projektiin. Yksittäinen julkinen vanha tapahtuma vahvistaa HTML-rakenteen; se ei takaa kaikkien käyttäjän tapahtumien saatavuutta.
- Kartan natiivi renderöinti, launcher-maskit ja asennus/Play Protect on vielä tarkistettava oikealla Android-laitteella. Robolectric-testit käyttävät kartalle testikorviketta.

## Lähteet

- [Metrixin API](https://discgolfmetrix.com/?ID=37&u=rule), [tapahtumalista](https://discgolfmetrix.com/?ID=60&u=rule) ja [julkinen esimerkkitapahtuma](https://discgolfmetrix.com/437198).
- [Androidin APK-allekirjoitus](https://developer.android.com/studio/publish/app-signing) ja [Google Play Protect](https://support.google.com/googleplay/answer/2812853).
- [Jsoupin Android-vaatimukset](https://jsoup.org/download): käytössä NIO core library desugaring vanhoja Android-versioita varten.
