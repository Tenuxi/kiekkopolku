# Kiekkopolku v0.2.0

- Aina tumma hiilenharmaa käyttöliittymä: ei vihreää teemaa.
- Pelaajan lisäys Metrix-ID:llä, integraatiokoodilla tai molemmilla. Näyttönimi on vapaaehtoinen.
- Integraatiokoodin tarkistus Metrixin API:lla ja salattu tallennus Android Keystoren avulla.
- Koodin liittäminen vanhaan ID-profiiliin sekä Metrix-profiilin avaaminen selaimeen.
- Room-päivitys säilyttää version 0.1.0 profiilit ja historiatiedot.

**Rajaus:** ID:n omistajuutta ei varmenneta, eikä oikeaa kierroshistoriaa vielä tuoda. Pelkkä koodi ei paljasta omistajan pelaaja-ID:tä dokumentoidussa vastauksessa. Koodin onnistunut liittäminen ei tarkoita kierrosten synkronointia. Käyttäjän testipelaajaa ei ole esitäytetty tai sisällytetty sovellukseen.

Versio **0.2.0**, versionCode **2**, paketti `fi.kiekkopolku.app.debug`, Android **8.0+**. Sama testiallekirjoitus kuin 0.1.0:ssa; APK on tarkoitettu päivitykseksi sen päälle. Fyysisen laitteen asennuskoetta ei tehty.

Varmennus: APK kääntyi, **26 testiä läpi**, lint **0 virhettä**, allekirjoitus tarkistettu. Oikeaa integraatiokoodia ei ollut käytettävissä verkkotestiin; hyväksytty vastausmuoto testattiin dokumentaation mukaisilla synteettisillä vastauksilla.

[Tarkistussumma](SHA256SUMS) · [Tekninen julkaisumuistio](../../docs/RELEASE-0.2.0.md) · [Asennusohje](../README.md)
