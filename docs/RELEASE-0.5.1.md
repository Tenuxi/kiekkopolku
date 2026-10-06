# Kiekkopolku 0.5.1

Kartta täyttää koko yläpalkin ja muuttumattoman alareunan navigaation välisen alueen. Paikallisen historian teksti, pelaajachipit, latauksen tekstirivi ja kartan tilasto-/seliterivit eivät vie kartan tilaa. Pelaajat valitaan yläpalkin suodatinpainikkeesta. Info-painike avaa vieritettävän paneelin, jossa ovat layout-määrät, puuttuvat sijainnit, väriselite, historian kattavuus ja kartan tekijätiedot. Näytä kaikki säilyy kompaktina overlay-painikkeena. Myös kartan alapuolen ylimääräinen 48 dp tilavaraus poistettiin; natiivin kartan tekijätietokontrolli säilyy. Kartan merkit, klusterit, datalähteet ja navigointitoiminnot eivät muuttuneet.

## Historiatuonnin ilmoitus

Aiemmin jokainen HISTORY_LIMIT-vastaus, myös vuorokauden välimuistista ohitettu rajoitus, asetti yleisen virhesyyn. Tuonti päättyi siten PARTIAL/FAILED-tulokseen ja pitkään snackbar-ilmoitukseen, vaikka sovellus oli käsitellyt kaiken saatavilla olevan aineiston. Lisäksi myöhempi HISTORY_LIMIT saattoi korvata aiemman ACCESS/RESPONSE-virheen ja estää ratatietojen virheen näkymisen.

Nyt kattavuusrajoitus erotetaan varsinaisesta tuontivirheestä: tilaksi tallennetaan LIMITED ja syyksi HISTORY_LIMIT, eikä pelkkä tunnettu rajoitus aiheuta automaattipäivityksen virheilmoitusta. Rajoitusta ei esitetä täydellisenä tuontina: se säilyy pelaajan asetuksissa, kartan infossa ja tilastoissa; täyden onnistuneen tuonnin aikaleimaa ei siirretä. Manuaalinen päivitys ilmoittaa rajoituksesta lyhyesti.

Aidot virheet säilyttävät varsinaisen syyn, ja myös julkisen metadatan verkkovirhe tallennetaan. Oikea virhe ja historiarajoitus voivat näkyä asetuksissa rinnakkain. Osittainen/epäonnistunut tuonti ilmoitetaan lyhyellä SnackbarDuration.Short-ilmoituksella, jossa on Asetukset-painike ja sulkemismahdollisuus. Asetukset avaa pelaajakohtaisen tallennetun syyn. Tallennettuja kierroksia ei poisteta.

## Tarkistukset

Compose-testit mittaavat kartan täyttävän koko käytettävissä olevan sisältöalueen normaalilla puhelimella, 320 × 480 dp näytöllä, vaakanäytöllä ja suurella tekstikoolla. Pelaajavalinta toimii myös tyhjennettäessä valinta. Karttamerkin ja puuttuvien sijaintien navigaatio, ilmoituksen automaattinen poistuminen ja siirtyminen virhesyyhyn on testattu. Repository-testit kattavat vuosirajoituksen ja aidon virheen yhdistelmän sekä välimuistin.

Activity käyttää adjustResize-asetusta näppäimistön kanssa. Scaffoldin järjestelmäpalkkivaraukset säilyvät, ne kulutetaan ennen imePadding-varauksia, ja kartalle siirtyminen poistaa tekstikentän fokuksen sekä sulkee näppäimistön. Dialogien sisältö vierittyy ja yläpalkin otsikko katkaistaan tarvittaessa. Robolectricin karttakorvike varmentaa Compose-mitoituksen; natiivikartan, fyysisen laitteen näppäimistön ja näyttöloven laitetestausta ei väitetä tehdyksi.

Kotlin-käännös ja debug/release-APK-käännökset onnistuivat. Kaikki 62 yksikkö-/Room-/Compose-testiä läpäisivät tarkistuksen, debug/release-lintissä ei ole virheitä. APK:t arkistoidaan versionumerokansioon samalla debug- ja release-avaimella kuin 0.5.0. Debug päivittää aiemman testiasennuksen; release päivittää aiemman release-asennuksen.
