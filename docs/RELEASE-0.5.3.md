# Kiekkopolku 0.5.3

## Sijaintirivin korjaus

Kartta ja ratakortit saavat tietonsa Roomista LocalHistoryRepository.snapshot()-muunnoksen ja yhteisen Course-mallin kautta. Kartta käyttää Course.latitude/longitude-arvoja, jotka tulevat Metrixin course-API:n Lat/Lng-kentistä tai sen ParentID:n osoittaman radan koordinaateista. Vanha ratakortin location()-apu katsoi vain city/country-kenttiä ja näytti niiden puuttuessa "Sijainti ei tiedossa", vaikka koordinaatit olisivat kelvolliset.

Kortti näyttää nyt olemassa olevan kaupungin, sen puuttuessa välimuistin osoitteen tai maan nimen. ISO-maakoodi muutetaan luettavaksi nimeksi. Jos luettavaa sijaintia ei ole, rivi jätetään pois. Koordinaatteja ei muuteta eikä paikkakuntaa arvata niiden tai radan nimen perusteella. Käänteistä geokoodausta tai erillistä korttien sijaintivarastoa ei lisätty.

Yhteiseen Course-malliin välitetään jo olemassa olevan course_metadata-välimuistin osoite ja positiivinen väylämäärä sekä tarvittaessa siellä oleva kaupunki/maa. Kun nykyinen koordinaattien varahaku jo lukee ParentID-radan tiedot, myös sen saatavilla oleva kaupunki/maa säilytetään, ellei layoutilla ole omaa arvoa. Uusia verkkohakuja tai tietokantamigraatiota ei tarvita. Radan koordinaatit ja ihmiselle luettava sijainti voivat edelleen olla eri tavoin saatavilla.

## Tiiviit kortit

Kortissa ovat radan/layoutin nimi, mahdollinen Ei käytössä -badge, tunnettu luettava sijainti, tunnettu väylämäärä (esimerkiksi 18 väylää), Viimeksi-päivä ja pelaajien nimet kierrosmäärineen. Ylimääräinen kokonaiskierrosrivi poistettiin. Pelaajakohtaiset luvut käyttävät samoja osallistumisia kuin ennen ja rivittyvät FlowRow-komponentilla. Nimeä, marginaaleja ja rivivälejä tiivistettiin nykyistä tummaa teemaa säilyttäen.

Ei käytössä -badge erotetaan vain nimen täsmällisestä sulkeistetusta (ei käytössä) -merkinnästä kirjainkoosta riippumatta. Ikää, nimeä tai puuttuvia tuloksia ei tulkita sulkemiseksi. Alkuperäinen nimi säilyy tietokannassa ja domain-mallissa, joten haku ja lajittelu eivät muutu. Kortin avaaminen käyttää samaa rata-ID:tä kuin ennen.

## Varmennus

70 testiä hyväksytty, Kotlin-käännös ja debug/release-APK-käännökset onnistuneet, lintissä ei virheitä. Uudet testit tarkistavat koordinaattien ja luettavan sijainnin eron, välimuistin väylämäärän välittymisen, tuntemattoman sijainnin piilotuksen, badgen erottamisen alkuperäistä nimeä muuttamatta sekä pelaajamäärien rivittymisen ja kortin avaamisen 320 dp puhelimella. [Testikuva](screenshots/v0.5.3/course-cards.png) käyttää synteettisiä pelaajia. Fyysistä Android-laitetta ei käytetty.

Päämuutokset: History.kt, LocalHistoryRepository.kt, KiekkopolkuApp.kt, strings.xml ja testit. APK:t arkistoidaan samoin allekirjoitusavaimin kuin aiemmin; debug päivittää debug-asennuksen ja release release-asennuksen.
