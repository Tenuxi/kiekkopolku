# Kiekkopolku

**Kiekkopolku – Kaikki kierroksesi. Yhdellä kartalla.**

Kiekkopolku on suomalaisille frisbeegolfaajille suunnattu henkilökohtainen frisbeegolf-historia ja karttasovellus. Se kokoaa pelatut radat, kierrokset, tulokset, acet ja vuosien varrella vieraillut paikat yhteen.

Brändissä yhdistyvät frisbeegolf, kartta, matka, historia ja luonto. Ilme on selkeä, rauhallinen ja helposti lähestyttävä. Sovelluksen värimaailma on tumma hiilenharmaa. Alkuperäinen logoluonnos yhdistää karttamerkin minimalistiseen frisbeegolfkoriin. Käyttöliittymä ja brändi ovat ensisijaisesti suomenkielisiä.

## Logoehdotus

- [Sovelluskuvakkeen luonnos](assets/brand/kiekkopolku-app-icon-concept.png) – vain symboli, ei tekstiä.
- [Täyden logon luonnos](assets/brand/kiekkopolku-full-logo-concept.png) – symboli, nimi ja slogan.
- [Generointipromptit](assets/brand/prompts.md).

Alkuperäiset vihreät kuvat ovat luonnoksia. Version 0.5.2 oma [harmaa sovelluskuvake](assets/brand/kiekkopolku-icon.svg) yhdistää karttapinnin ja korin. Android-resursseissa ovat adaptive foreground/background, monochrome, viisi PNG-tiheyttä sekä splash-logo. Kuvakkeessa ei ole tekstiä.

## Sovelluksen suunnitelma

[PHASE 1: tutkimus ja arkkitehtuuri](docs/PHASE-1.md) sisältää integraatiolöydökset, epävarmuudet, ehdotetun Room-mallin, pakettirakenteen, MVP-rajauksen ja vaiheittaisen toteutussuunnitelman.

## Android-sovellus — v0.5.2

Android-projekti on kansiossa [kiekkopolku-android](kiekkopolku-android/). Avaa tämä kansio Android Studiossa. Sovellus sisältää pelaajaprofiilit, monivalinnan, ratalistan, kierrokset ja väylätulokset, acet sekä tilastot. Tiedot tallennetaan Roomiin. Esimerkkiperhe ladataan vain käyttäjän valinnasta.

- [Päivitä nykyinen testisovellus: 0.5.2 debug-APK](https://github.com/Tenuxi/kiekkopolku/raw/refs/heads/main/apk-releases/0.5.2/kiekkopolku-0.5.2-debug.apk)
- [Lataa allekirjoitettu release-APK](https://github.com/Tenuxi/kiekkopolku/raw/refs/heads/main/apk-releases/0.5.2/kiekkopolku-0.5.2-release.apk) — erillinen sovellus; lisää profiilit uudelleen. Nykyisen testisovelluksen päivittämiseen käytä yllä olevaa debug-APK:ta.
- [Lataa version 0.5.2 lähdekoodi ZIP-pakettina](https://github.com/Tenuxi/kiekkopolku/archive/refs/tags/v0.5.2.zip) — Android-projekti on paketissa omassa `kiekkopolku-android`-kansiossaan.
- [APK-arkisto ja vanhan version asennusohje](apk-releases/README.md)
- [Käännös- ja käyttöohje](kiekkopolku-android/README.md)
- [Version 0.5.2 muutokset ja integraation rajat](docs/RELEASE-0.5.2.md)
- [PHASE 2:n toteutus ja testitulokset](docs/PHASE-2.md)

Tarvitset vähintään Android 8.0:n. APK on testikäyttöön allekirjoitettu debug-versio. Pelaajan voi lisätä ID:llä, integraatiokoodilla tai molemmilla. Koodi tarkistetaan Metrixistä ja tallennetaan salattuna; ID:n omistajuutta ei varmenneta. Kierroshistorian tuonti tarvitsee sekä pelaaja-ID:n että integraatiokoodin. Sovellus tarkistaa tallennetut pelaajat avautuessaan; päivityskuvake tekee valituille pelaajille täyden uusintahaun. Tuodut radat/layoutit, kierrokset ja väylätulokset säilyvät paikallisesti. Metrix voi rajoittaa vanhan historian saatavuutta. Kartta näyttää sijainnilliset pelatut radat vaalealla OpenFreeMap Positron -taustakartalla. Kartta täyttää ylä- ja alapalkin välisen tilan; selitteet löytyvät info-painikkeesta ja pelaajavalinta yläpalkin suodattimesta. Vihreä koripinni tarkoittaa viimeisen 12 kuukauden käyntiä, oranssi vain vanhempia käyntejä. Julkisilta Metrix-tapahtumasivuilta varmennetut vanhat käynnit tallennetaan ilman tuloskorttia. Kaikkien rajoitettujen tapahtumien tietoja ei välttämättä saada. Tilastoissa viimeiset 12 kuukautta ja koko historia näkyvät kompakteina 2×2-yhteenvetoina. Lisätiedot, Metrix-kattavuus ja laskentaselitykset avataan erikseen. Pelaajakohtainen erittely näytetään vain usean pelaajan valinnassa. Frisbeegolfradat.fi-integraatio ei ole käytössä.

Version lähde on `kiekkopolku-android/version.properties`. Commit-viestit alkavat sovelluksen versionumerolla, esimerkiksi `[v0.5.2]`. Ota tarkistus käyttöön kloonauksen jälkeen: `git config core.hooksPath .githooks`. Julkaistu APK säilytetään muuttumattomana versionumerokansiossa ja vastaava lähdekoodi Git-tagissa.
