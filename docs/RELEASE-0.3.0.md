# Kiekkopolku 0.3.0 — oma Metrix-historia

VersionCode 3. Päivitys version 0.2.0 päälle säilyttää profiilit, salatut koodit ja paikallisen historian. Room-skeema on edelleen 2; uutta migraatiota ei tarvita. Esimerkkiperhe on vapaaehtoinen.

## Käyttö

1. Asenna 0.3.0-APK aiemman testisovelluksen päälle.
2. Avaa **Pelaajat ja asetukset → Täydennä Metrix-tiedot**. Tuontiin tarvitaan sekä pelaaja-ID että integraatiokoodi. Jo tallennettua koodia ei tarvitse kirjoittaa uudelleen ID:tä täydennettäessä.
3. Tallennus käynnistää haun. Myös sovelluksen avaaminen/etualalle palaaminen tarkistaa tallennetut oikeat pelaajat taustalla. Alle minuutin toistuvia avauksia ja käynnissä olevan haun päällekkäistä käynnistystä vältetään.
4. Refresh-kuvakkeen paikalla pyörii latausilmaisin, ja haun eteneminen näkyy yläpalkissa. Aiemman historian selaaminen ja pelaajien suodattaminen toimivat haun aikana. Asetuksista näet mahdollisen pelaajakohtaisen virheen.
5. Päivityskuvake hakee valittujen pelaajien koko saatavilla olevan historian uudelleen. Uusintahaku ei monista kierroksia tai aceja.

## Tuonti

`my_competitions` palauttaa tapahtumalistan, `result` niiden tulokset. Oman pelaajan osallistuminen tunnistetaan täsmällisellä `UserID`:llä. Vain lisättyjen pelaajien tulokset tallennetaan; muiden osallistujien vastauksessa olevia nimiä tai tuloksia ei tallenneta tietokantaan. Koodin omistajuutta ei päätellä osallistujalistasta.

Tuonti sisältää radan/layoutin nimen ja tunnisteen, kierroksen päiväyksen, kokonaistuloksen, eron pariin sekä saatavilla olevat väylätulokset. Tilastot ja tunnetut acet muodostuvat samasta paikallisesta historiasta. Monikierrostapahtuman yhteenvetoa ei lasketa kierrokseksi; lapsikierrokset käsitellään kerran. Vastauksessa valmiiksi olevat lapsikierrokset hyödynnetään ilman erillistä verkkopyyntöä.

Kierrokset tallennetaan transaktioina haun edetessä. Verkkovirhe, peruutus tai yhden tapahtuman käyttörajoitus ei poista jo tallennettua historiaa. Osittainen vastaus ei korvaa aiempaa täydellistä tuloskorttia. Epäonnistunut haku ei siirrä viimeisen onnistuneen synkronoinnin aikaa eteenpäin. Tapahtuman puuttuminen kilpailulistalta ei poista paikallista historiaa.

Automaattihaku käyttää alle vuorokauden vanhaa paikallista kopiota yli 30 päivää vanhoista valmiista kierroksista. Muut haetaan uudelleen. Manuaalinen päivitys ohittaa välimuistin. Pyynnöt ovat peräkkäisiä, tulospyyntöjen välillä on 250 ms tauko. Haku jatkuu sovelluksen ollessa elossa; tämä versio ei ajasta suljetun sovelluksen jatkuvaa taustatyötä. Katkenneen haun voi käynnistää uudelleen; jo tallennetut tiedot säilyvät.

## Saatavuus ja rajat

- Metrix voi rajata yli vuoden vanhan historian saatavuutta. Sovellus näyttää tunnistetun rajoituksen ja Metrixin yhteysosoitteen. Onnistunut tarkistus tarkoittaa API:n antaman listan käsittelyä, ei todistettua koko uran kattavuutta.
- Pelkkä ID tai pelkkä koodi riittää profiilin luomiseen, mutta tähän tuontiin tarvitaan molemmat. Puuttuva tieto kerrotaan asetuksissa.
- MetrixMode 0 ja 1:n väylätulosrakenteet tuetaan. Julkisessa parikilpailussa havaittu Type 6 ohitetaan. Muita tuntemattomia rakenteita ei arvata yksilötuloksiksi.
- Layout-ID:t ovat erillisiä ratakohteita; käyttöliittymä kertoo laskennan koskevan ratoja/layouteja. Fyysisten ratojen yhdistäminen, koordinaattien täydennys ja kartta eivät kuulu tähän versioon.
- Salatut integraatiokoodit pysyvät laitteessa ja niitä lähetetään vain Metrixin HTTPS-rajapintaan. Ei koodillisten URL:ien lokitusta, HTTP-levyvälimuistia tai uudelleenohjauksia. Raakoja palveluvirheitä ei tallenneta tai näytetä.
- Käyttäjän testipelaaja-ID ei ole sovelluksessa, esimerkkidatassa tai testeissä.

## Varmennus

Kaikki 40 testiä läpäisivät. Lint: 0 virhettä, 30 varoitusta (riippuvuuksien päivitysehdotukset ja UseKtx). Debug-APK:n allekirjoitus tarkistettu; sama avain kuin aiemmissa versioissa.

Automaattitesteissä tarkistetaan idempotentti tuonti, ID-kohdistus, yhteiset perhekierrokset, hierarkiat ja sisäkkäiset vastaukset, osittainen virhe, offline-tila, vanhan tuloskortin säilyminen, peruutus, automaattinen päivitys ja latauskuvake. Lisäksi mukana ovat aiemmat Room-migraatio-, salaus-, domain- ja UI-testit.

Nykyinen julkinen oikea tulosvastaus on tarkistettu mapperin läpi erillisestä väliaikaistiedostosta. Se ei sisälly lähdekoodiin eikä APK:hon. Valinnainen `MetrixLiveContractTest` ottaa tiedostopolun ympäristömuuttujasta `METRIX_PUBLIC_RESULT_FIXTURE`; normaalissa offline-testiajossa se ohitetaan.

Oman tilin yksityinen historia ja laitekohtainen verkkoyhteys on vielä varmennettava puhelimessa: kehitysympäristöllä ei ole pääsyä puhelimen tallennettuun koodiin. Julkinen tulosrajapinta ja dokumentoitu koodillinen historiapolku on erotettu testauksessa.

## Lähteet ja live-havainnot (5.10.2026)

- [Metrix: My competitions](https://discgolfmetrix.com/?ID=60&u=rule)
- [Metrix: Get Results](https://discgolfmetrix.com/?ID=38&u=rule)
- Julkiset tulosvastaukset [3805786](https://discgolfmetrix.com/api.php?content=result&id=3805786) ja [3793553](https://discgolfmetrix.com/api.php?content=result&id=3793553): numeerinen Competition.ID, MetrixMode 1, HasSubcompetitions 0 ja puuttuva SubCompetitions-kenttä. PlayerResults kohdistuu Tracks-taulukon järjestykseen.
- [Monikierrostapahtuma 3524744](https://discgolfmetrix.com/api.php?content=result&id=3524744): HasSubcompetitions 1, SubCompetitions sisältää kokonaisia lapsikierroksia.
- [Parikilpailu 3552420](https://discgolfmetrix.com/api.php?content=result&id=3552420): Type 6 ja tulosten UserID null.
- [Vanha dokumentaatioesimerkki](https://discgolfmetrix.com/api.php?content=result&id=437198): Competition null ja vanhan datan käyttörajoitus.
