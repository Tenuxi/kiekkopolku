# Kiekkopolku 0.5.2

Tilastot-näkymän yläpalkissa on Tilastot-otsikko, jonka alla säilyy pelaajavalinta. Viimeisten 12 kuukauden kortti tulee ensimmäisenä ja koko tallennettu historia sen jälkeen. Neljä päälukua (kierrokset, fyysiset radat, layoutit ja acet) muodostavat molemmissa korteissa 2×2-ruudukon. Numeroissa käytetään nykyistä vaaleaa korostusväriä hiilenharmaalla taustalla; acen yhteydessä on pieni tähti. Aikaväli näkyy tiiviisti ruudukon alapuolella. Kortit käyttävät kohtuullisia sisämarginaaleja ja joustavaa korkeutta.

Lisätiedot sisältää ennallaan erilliset kierrostapahtumat, ensimmäisen ja viimeisimmän päivämäärän sekä puuttuvat väylä- ja ratatiedot. Metrix-datan kattavuus ja Miten tilastot lasketaan? ovat erillisiä oletuksena suljettuja osioita. Niissä säilyvät aiemmat kattavuusluvut, keskeneräiset haut, rajoitukset ja laskentaselitykset. Osioiden painikkeet kertovat saavutettavuusrajapinnassa avatun/suljetun tilan; korttikohtainen Lisätiedot-painike kertoo myös kortin nimen. Avaustila säilyy rememberSaveable-tilassa.

Aiempi pelaajakohtainen kortti syntyi valittujen pelaajien ehdottomasta silmukasta. Yhden valitun pelaajan kortti siis toisti täsmälleen koko historian yhteenvedon. Se jätetään nyt pois. Usealla valitulla pelaajalla Pelaajittain-osio tarjoaa hyödyllisen erittelyn yhdistetyn historian rinnalle; pelaajan Metrix-tapahtumatunnisteiden määrä säilyy hänen lisätiedoissaan.

Tilastojen laskentaa, aikarajoja, tietokantaa tai Metrix-tuontia ei muutettu. Esimerkkilukuja ei kovakoodattu sovellukseen. Näkymä käyttää nykyistä Compose/Material 3 -toteutusta ilman uutta UI-kirjastoa. Kartan ja muiden välilehtien toiminta säilyy.

Päämuutokset: KiekkopolkuApp.kt, strings.xml ja UiTest.kt; lisäksi versionumerot, dokumentaatio ja APK-arkisto. Testit kattavat korttien järjestyksen, ajankohtaisten päälukujen näkyvyyden ilman vieritystä, kapean 320 dp puhelimen, suuren tekstikoon, lisätietojen avaamisen/sulkemisen, teknisten tietojen säilymisen ja usean pelaajan erittelyn. Natiivin Android-laitteen visuaalista testausta ei väitetä tehdyksi.

Varmennus: 66 testiä hyväksytty, debug/release-lintissä 0 virhettä, Kotlin- ja APK-käännökset onnistuivat. [Tilastonäkymän testikuva](screenshots/v0.5.2/statistics.png) käyttää synteettistä esimerkkipelaajaa. APK:t on allekirjoitettu samoilla avaimilla kuin aiemmat versiot.
