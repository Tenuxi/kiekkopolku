# Kiekkopolku 0.4.0 — kartta ja historian kattavuus

VersionCode 4. APK päivitetään 0.3.0:n päälle samalla debug-allekirjoituksella. Room 2 → 3 lisää tapahtumaindeksin ja säilyttää vanhat profiilit, salatut koodit, radat, kierrokset ja väylätulokset. Myös 1 → 2 → 3 -päivityspolku tuetaan.

## Käyttäjälle

- **Kartta** on uusi välilehti. Tumma karttapohja, zoomaus ja siirtely, lähekkäisten ratamerkkien ryhmittely sekä Näytä kaikki -painike. Napauta merkkiä avataksesi radan/layoutin historian; päällekkäiset layoutit voi valita luettelosta. Pelaajasuodatin rajaa myös karttaa.
- Puuttuvien sijaintien määrä ja luettelo näkyvät kartassa. Sijainteja haetaan Metrixin ratatiedoista. Jos layoutilta puuttuu sijainti, käytetään API:n vahvistaman emoradan koordinaatteja, mikäli ne saadaan.
- **Radat** avautuu oletuksena järjestyksessä **Viimeksi pelattu**. Aakkosjärjestys ja muut vaihtoehdot ovat edelleen valittavissa.
- **Tilastot** näyttää koko tallennetun historian: varmennetut pelatut kierrokset, tunnetut radat, pelatut layoutit, tunnetut acet ja päivämäärävälin. Erillinen **Viimeiset 12 kuukautta** -osio rajaa saman datan liukuvaan vuoteen.
- Metrix-listan tapahtumatunnisteiden, vanhan historian rajoittamien hakujen, muiden puuttuvien hakujen ja keskeneräisten hakujen määrät näkyvät erikseen. Tapahtumaluettelo säilytetään, vaikka tuloskorttia ei saataisi.

Asennuksen jälkeen sovellus täydentää jo tallennettujen ratojen koordinaatteja automaattisesti. Yläreuna kertoo ratatietojen hausta. Päivityskuvake tekee täyden uusintahaun. Ensimmäinen täydennys voi kestää ratojen ja tapahtumien määrästä riippuen.

## Mitä yli 800 latausta tarkoittaa

Metrixin `my_competitions` palauttaa tapahtumien tunnisteita. Ne voivat sisältää yksittäisiä kierroksia, monikierroskilpailuja, yhteenvetoja ja muita tapahtumia. Tunniste ei yksin sisällä pelipäivää, ratatunnistetta, sijaintia tai käyttäjän varmennettua osallistumista.

Tästä syystä tapahtumalistan kokoa ei merkitä pelattujen kierrosten määräksi. Estetystä tunnisteesta ei luoda keksittyä kierrosta tai karttamerkkiä. Kaikki jo tuodut vanhat kierrokset säilyvät koko historian luvuissa ja kartalla, kun niiden ratakoordinaatit ovat tiedossa. Jos Metrix estää vanhan tuloshaun, tästä kerrotaan tilastoissa. Viimeisen vuoden osio ei väitä koko vuoden tuloksia täydellisiksi.

Tunnetut radat yhdistetään laskennassa Metrixin ParentID:llä. Ilman vahvistettua parent-tietoa layout on oma ratakohteensa; nimi- tai etäisyysarvauksia ei tehdä. Kartalla layoutit säilyvät valittavina erikseen.

## Toteutus

- MapLibre Native 11.13.5, OpenFreeMapin `dark`-tyyli, GeoJSON-pisteet ja klusterit. API-avainta tai maksullista Google-projektia ei tarvita.
- Karttatiedot pysyvät paikallisessa GeoJSON-lähteessä. Tile-palvelulle ei lähetetä Metrix-koodia, pelaajien nimiä tai tuloksia. Karttaruutujen verkkopyynnöt paljastavat palvelulle katsotun kartta-alueen normaalisti.
- Sovellus ei pyydä GPS-sijaintia. Karttakirjaston lisäämät sijaintiluvat poistetaan yhdistetystä manifestista. Verkkotilan luku sallitaan.
- MapView käynnistetään/pysäytetään Activityn elinkaaren mukana ja vapautetaan karttanäkymän poistuessa. Tyylin latausvirheessä näkyy uudelleenyritys. Taustakartta tarvitsee verkon tai aiemman SDK-välimuistin; sovellus ei lataa offline-alueita.
- Metrix `course`: koordinaatit, nimi, kaupunki, maa, parent, väylämäärä ja lähdetiedot. Virheellinen koordinaatti tai väärä rata-ID ei synnytä karttamerkkiä. Ratametadata säilyy tuloskortin uusintahaussa.
- Ratatiedot täydentyvät myös aiemmille kierroksille. Metatiedon automaattinen välimuisti seitsemän päivää; vanhan historian estettyjen tuloshakujen välimuisti yksi vuorokausi. Manuaalinen päivitys ohittaa molemmat.
- Tapahtumaindeksi säilyttää alkuperäisen listajäsenyyden ja hakutilan per pelaaja. Pelaajan poisto poistaa myös hänen indeksinsä; muiden pelaajien historia säilyy.

## Testaus ja rajat

51 testiä läpäisi, 0 epäonnistumista. Lintissä 0 virhettä. APK:n allekirjoitus tarkistettu ja sama kuin aiemmissa versioissa. Yhdistetty manifesti tarkistettu: ei sijaintilupia.

Testataan Room-migraatiot, 800 estetyn tapahtuman säilyminen ilman kuvitteellisia kierroksia, koordinaattien täydennys ja säilyminen, parent-yhdistäminen, viimeisen vuoden päivämääräraja, suodatuksen vaikutus karttapisteisiin, oletusjärjestys ja kartasta historiaan navigointi. Compose-karttatesteissä natiivirenderöijä korvataan testimerkillä: ne tarkistavat sovelluksen suodatuksen ja navigoinnin, eivät OpenGL-piirtoa.

Natiivin kartan piirtymistä ja eleitä ei ole ajettu Android-laitteessa tässä kehitysympäristössä. Karttatyylin ja kirjaston saatavuus sekä käännös tarkistetaan. Oman Metrix-koodin ratavastaukset testataan puhelimessa; kehitysympäristöllä ei ole pääsyä tallennettuun koodiin. Ratavastauksen muunnostestit käyttävät dokumentoitua rakennetta ja synteettistä dataa.

## Lähteet

- [Metrix My competitions](https://discgolfmetrix.com/?ID=60&u=rule)
- [Metrix Course: Lat/Lng ja ParentID](https://discgolfmetrix.com/?ID=50&u=rule)
- [MapLibre Android](https://maplibre.org/maplibre-native/android/examples/getting-started/)
- [MapLibre-klusterit](https://maplibre.org/maplibre-native/android/examples/styling/circle-layer/)
- [OpenFreeMap: mobiilikäyttö ja karttatyylit](https://openfreemap.org/quick_start/)
- [OpenStreetMap-tekijät](https://www.openstreetmap.org/copyright)
