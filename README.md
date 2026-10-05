# Kiekkopolku

**Kiekkopolku – Kaikki kierroksesi. Yhdellä kartalla.**

Kiekkopolku on suomalaisille frisbeegolfaajille suunnattu henkilökohtainen frisbeegolf-historia ja karttasovellus. Se kokoaa pelatut radat, kierrokset, tulokset, acet ja vuosien varrella vieraillut paikat yhteen.

Brändissä yhdistyvät frisbeegolf, kartta, matka, historia ja luonto. Ilme on selkeä, rauhallinen ja helposti lähestyttävä. Sovelluksen värimaailma on tumma hiilenharmaa. Alkuperäinen logoluonnos yhdistää karttamerkin minimalistiseen frisbeegolfkoriin. Käyttöliittymä ja brändi ovat ensisijaisesti suomenkielisiä.

## Logoehdotus

- [Sovelluskuvakkeen luonnos](assets/brand/kiekkopolku-app-icon-concept.png) – vain symboli, ei tekstiä.
- [Täyden logon luonnos](assets/brand/kiekkopolku-full-logo-concept.png) – symboli, nimi ja slogan.
- [Generointipromptit](assets/brand/prompts.md).

Kuvat ovat hyväksyttäväksi tarkoitettuja rasteriluonnoksia, eivät valmiita Android-resursseja. Hyväksymisen jälkeen tehdään adaptive launcher iconin foreground ja background, themed icons -toiminnon monochrome-kuvake, tarvittavat mipmap-versiot sekä splash screen -logo. Tuotantoversioissa viimeistellään tasaiset värit, läpinäkyvyys, suoja-alueet ja toimivuus pienissä ko'oissa. Slogan pidetään poistettavana osana täyttä logoa.

## Sovelluksen suunnitelma

[PHASE 1: tutkimus ja arkkitehtuuri](docs/PHASE-1.md) sisältää integraatiolöydökset, epävarmuudet, ehdotetun Room-mallin, pakettirakenteen, MVP-rajauksen ja vaiheittaisen toteutussuunnitelman.

## Android-sovellus — v0.2.0

PHASE 2:n paikallinen MVP on kansiossa [kiekkopolku-android](kiekkopolku-android/). Avaa tämä kansio Android Studiossa. Sovellus sisältää pelaajaprofiilit, monivalinnan, ratalistan, kierrokset ja väylätulokset, acet sekä tilastot. Tiedot tallennetaan Roomiin. Esimerkkiperhe ladataan vain käyttäjän valinnasta.

- [Lataa Kiekkopolku 0.2.0 APK](https://github.com/Tenuxi/kiekkopolku/raw/refs/heads/main/apk-releases/0.2.0/kiekkopolku-0.2.0-debug.apk)
- [Lataa version 0.2.0 lähdekoodi ZIP-pakettina](https://github.com/Tenuxi/kiekkopolku/archive/refs/tags/v0.2.0.zip) — Android-projekti on paketissa omassa `kiekkopolku-android`-kansiossaan.
- [APK-arkisto ja vanhan version asennusohje](apk-releases/README.md)
- [Käännös- ja käyttöohje](kiekkopolku-android/README.md)
- [Version 0.2.0 muutokset ja integraation rajat](docs/RELEASE-0.2.0.md)
- [PHASE 2:n toteutus ja testitulokset](docs/PHASE-2.md)

Tarvitset vähintään Android 8.0:n. APK on testikäyttöön allekirjoitettu debug-versio. Pelaajan voi lisätä ID:llä, integraatiokoodilla tai molemmilla. Koodi tarkistetaan Metrixistä ja tallennetaan salattuna; ID:n omistajuutta ei varmenneta. Varsinainen kierroshistorian tuonti ja kartta tulevat myöhemmissä vaiheissa. Metrixin koko historian saatavuus ja Frisbeegolfradat.fi:n integraatiolupa ovat edelleen avoimia.

Version lähde on `kiekkopolku-android/version.properties`. Commit-viestit alkavat sovelluksen versionumerolla, esimerkiksi `[v0.2.0]`. Ota tarkistus käyttöön kloonauksen jälkeen: `git config core.hooksPath .githooks`. Julkaistu APK säilytetään muuttumattomana versionumerokansiossa ja vastaava lähdekoodi Git-tagissa.
