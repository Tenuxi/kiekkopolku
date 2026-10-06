package fi.kiekkopolku.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import fi.kiekkopolku.app.domain.*
import fi.kiekkopolku.app.ui.HistoryViewModel
import fi.kiekkopolku.app.ui.KiekkopolkuApp
import fi.kiekkopolku.app.ui.CourseMap
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Assert.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "fi-rFI-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Fake : HistoryRepository, PlayerRepository, SyncRepository {
        override val history = MutableStateFlow(History())
        var opened = 0
        var refreshResult = RefreshResult.UPDATED_METRIX
        var refreshGate: CompletableDeferred<Unit>? = null
        override val progress = MutableStateFlow<SyncProgress?>(null)
        override suspend fun refreshOnOpen(): RefreshResult {
            opened++
            if (refreshGate != null) {
                progress.value = SyncProgress("Testipelaaja", 1, 3)
                refreshGate!!.await()
                progress.value = null
            }
            return refreshResult
        }
        override suspend fun addPlayer(metrixId: String, name: String, integrationCode: String, profileId: String?) {
            history.value = history.value.copy(players = history.value.players + Player(metrixId.ifBlank { "linked" }, metrixId.ifBlank { null }, name, 0, true, false, null, integrationCode.isNotBlank()))
        }
        override suspend fun selectPlayer(id: String, selected: Boolean) { history.value = history.value.copy(players = history.value.players.map { if (it.id == id) it.copy(active = selected) else it }) }
        override suspend fun deletePlayer(id: String) { history.value = history.value.copy(players = history.value.players.filterNot { it.id == id }) }
        override suspend fun loadSample() {
            history.value = History(listOf(Player("p", "sample:1", "Minä (esimerkki)", 0, true, true, 42)),
                listOf(Course("c", "Metsäpolku", "Tampere", "FI", null, null)),
                listOf(RoundEntry("r", "r", "sample", "p", "c", "2026-05-01", "9 väylää", null, 1, -2, "FINISHED", true, true, listOf(Hole(0, "1", 3, 1)))))
        }
        override suspend fun removeSample() { history.value = History() }
        override suspend fun refreshSelected() = refreshResult
    }
    private fun start(): Fake {
        val fake = Fake()
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm) }
        compose.waitForIdle()
        return fake
    }
    @Test fun openingRefreshShowsSpinnerWhileCachedRoundsRemainNavigable() {
        val fake = Fake()
        runBlocking { fake.loadSample() }
        val gate = CompletableDeferred<Unit>()
        fake.refreshGate = gate
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm) }
        compose.onNodeWithContentDescription("Päivitetään Metrix-tietoja").assertIsDisplayed()
        compose.onNodeWithContentDescription("Päivitä tiedot").assertDoesNotExist()
        compose.onNodeWithText("Metsäpolku").assertIsDisplayed().performClick()
        compose.onNodeWithText("Koordinaatteja ei ole tallennettu.").assertIsDisplayed()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, fake.opened)
            vm.onOpen() // Busy/rotation guards must not enqueue another import.
            org.junit.Assert.assertEquals(1, fake.opened)
            gate.complete(Unit)
        }
        compose.onNodeWithContentDescription("Päivitä tiedot").assertIsDisplayed()
        compose.onNodeWithContentDescription("Päivitetään Metrix-tietoja").assertDoesNotExist()
    }
    @Test fun welcomeSampleCourseAndHoleNavigation() {
        start()
        compose.onNodeWithText("Oma kiekkopolkusi alkaa tästä").assertIsDisplayed()
        compose.onNodeWithText("Tutustu esimerkkiperheeseen").performClick()
        compose.waitForIdle()
        // Draw the activity directly: PixelCopy/forceRedraw is not supported reliably by Robolectric.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val screenshot = File("build/reports/screenshots/courses.png").apply { parentFile?.mkdirs() }
            screenshot.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Metsäpolku").assertIsDisplayed().performClick()
        compose.onNodeWithText("Koordinaatteja ei ole tallennettu.").assertIsDisplayed()
        compose.onAllNodesWithText("Metsäpolku").onLast().performScrollTo().performClick()
        compose.onNodeWithText("1 · ACE").performScrollTo().assertIsDisplayed()
    }
    @Test fun mapTabUsesSelectedCoursesAndOpensHistoryFromMarkerAndMissingList() {
        val fake = Fake()
        runBlocking { fake.loadSample() }
        val original = fake.history.value
        fake.history.value = original.copy(courses = original.courses.map { it.copy(latitude = 61.5, longitude = 23.7) } +
            Course("missing", "Sijainniton rata", null, null, null, null),
            entries = original.entries + original.entries.first().copy(roundId = "r2", courseId = "missing", date = "2020-01-01"))
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm, mapScreen = { h, open -> CourseMap(h, open, mapContent = { located, select ->
            Button(onClick = { select(listOf(located.first().course.id)) }) { Text("Testimerkki") }
        }) }) }
        compose.onNodeWithText("Viimeksi pelattu").assertIsDisplayed()
        compose.onNodeWithText("Kartta").performClick()
        compose.onNodeWithText("Kartalla layouteja: 1").assertDoesNotExist()
        compose.onNodeWithText("Testimerkki").performClick()
        compose.onNodeWithText("Metsäpolku").performClick()
        compose.onNodeWithText("Koordinaatit: 61,50000, 23,70000").assertExists()
        compose.onNodeWithContentDescription("Takaisin").performClick()
        compose.onNodeWithContentDescription("Kartan tiedot").performClick()
        compose.onNodeWithText("Kartalla layouteja: 1").assertIsDisplayed()
        compose.onNodeWithText("Sijainti puuttuu: 1").performClick()
        compose.onNodeWithText("Sijainniton rata").performClick()
        compose.onNodeWithText("Koordinaatteja ei ole tallennettu.").assertIsDisplayed()
    }
    private fun checkMapFillsAvailableSpace(fontScale: Float = 1f) {
        val fake = Fake()
        runBlocking { fake.loadSample() }
        fake.refreshResult = RefreshResult.HISTORY_LIMITED
        fake.history.value = fake.history.value.copy(players = fake.history.value.players.map { it.copy(sample=false, syncError="HISTORY_LIMIT", syncStatus="LIMITED") })
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            KiekkopolkuApp(vm, mapScreen = { h, open -> CourseMap(h, open, mapContent = { _, _ ->
                Box(Modifier.fillMaxSize().testTag("map-surface"))
            }) })
        } }
        compose.onNodeWithText("Kartta").performClick()
        compose.onNodeWithText("Historia tallessa tässä laitteessa").assertDoesNotExist()
        compose.onNodeWithText("Minä (esimerkki)").assertDoesNotExist()
        compose.onNodeWithText("Kartalla layouteja: 0").assertDoesNotExist()
        compose.onNodeWithText("Metrix-tuonti jäi osittaiseksi.", substring=true).assertDoesNotExist()
        val map = compose.onNodeWithTag("map-surface").fetchSemanticsNode().boundsInRoot
        val available = compose.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot
        assertEquals(available.top, map.top, 1f)
        assertEquals(available.bottom, map.bottom, 1f)
        assertEquals(available.width, map.width, 1f)
        assertTrue(map.height > 100f)
        compose.onNodeWithContentDescription("Kartan pelaajavalinta").performClick()
        compose.onNodeWithText("Minä (esimerkki)").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Sulje").performClick()
        compose.onNodeWithTag("map-surface").assertIsDisplayed()
        compose.onNodeWithText("Valitse pelaaja yläpalkin suodattimesta.").assertIsDisplayed()
    }
    @Test fun mapFillsAvailableSpaceWithLargeText() = checkMapFillsAvailableSpace(1.8f)
    @Test fun mapFillsPortraitAndFiltersRemainAvailable() = checkMapFillsAvailableSpace()
    @Test @Config(qualifiers = "fi-rFI-w320dp-h480dp")
    fun mapFillsSmallPhone() = checkMapFillsAvailableSpace()
    @Test @Config(qualifiers = "fi-rFI-w891dp-h411dp-land")
    fun mapFillsLandscape() = checkMapFillsAvailableSpace()

    @Test fun actualImportFailureIsTemporaryAndItsActionOpensPersistedPlayerReason() {
        val fake = Fake()
        fake.history.value = History(players=listOf(Player("p", "123", "Oma", 0, true, false, null, true, "ERROR", "CONNECTION")))
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm, mapScreen = { h, open -> CourseMap(h, open, mapContent = { _, _ -> Box(Modifier.fillMaxSize()) }) }) }
        compose.onNodeWithText("Kartta").performClick()
        compose.runOnIdle { fake.refreshResult = RefreshResult.FAILED; vm.refresh() }
        compose.onNodeWithText("Metrix-päivitys epäonnistui.", substring=true).assertIsDisplayed()
        compose.onNodeWithText("Asetukset").performClick()
        compose.onNodeWithText("Verkkohaku keskeytyi.", substring=true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { vm.message.value = R.string.sync_partial }
        compose.onNodeWithText("Metrix-tuonti jäi osittaiseksi.", substring=true).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6000)
        compose.waitForIdle()
        compose.onNodeWithText("Metrix-tuonti jäi osittaiseksi.", substring=true).assertDoesNotExist()
    }
    private fun scrollStatisticsTo(text: String) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }
    private fun startStatistics(fontScale: Float = 1f): Fake {
        val fake = Fake()
        runBlocking { fake.loadSample() }
        fake.history.value = fake.history.value.copy(entries = fake.history.value.entries.map {
            it.copy(date = java.time.LocalDate.now().minusDays(1).toString())
        })
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            KiekkopolkuApp(vm)
        } }
        compose.onNodeWithText("Tilastot").performClick()
        return fake
    }
    @Test fun statisticsPutRecentGridFirstAndKeepDetailsClosed() {
        startStatistics()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/screenshots/statistics.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val year = compose.onNodeWithTag("stats-year").fetchSemanticsNode().boundsInRoot
        val all = compose.onNodeWithTag("stats-all").fetchSemanticsNode().boundsInRoot
        assertTrue(year.top < all.top)
        assertTrue(year.bottom <= compose.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNode(hasText("1") and hasText("Kierrosta") and hasAnyAncestor(hasTestTag("stats-year"))).assertIsDisplayed()
        compose.onNodeWithTag("stats-player").assertDoesNotExist()
        compose.onNodeWithText("Pelaajittain").assertDoesNotExist()
        compose.onNodeWithText("Erillisiä kierrostapahtumia:", substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Lisätiedot: Viimeiset 12 kuukautta").performClick()
        compose.onNodeWithText("Erillisiä kierrostapahtumia:", substring=true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Lisätiedot: Viimeiset 12 kuukautta").performClick()
        compose.onNodeWithText("Erillisiä kierrostapahtumia:", substring=true).assertDoesNotExist()
        scrollStatisticsTo("Miten tilastot lasketaan?")
        compose.onNodeWithContentDescription("Miten tilastot lasketaan?").performClick()
        compose.onNodeWithText("Kierrosmäärä", substring=true).performScrollTo().assertIsDisplayed()
    }
    @Test @Config(qualifiers = "fi-rFI-w320dp-h480dp")
    fun statisticsGridFitsNarrowPhoneWithoutScrolling() {
        startStatistics()
        val card = compose.onNodeWithTag("stats-year").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom <= content.bottom)
        for (label in listOf("Kierrosta", "Rataa", "Layoutia", "Ace")) {
            val node = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("stats-year"))).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(node.left >= card.left && node.right <= card.right)
        }
    }
    @Test @Config(qualifiers = "fi-rFI-w320dp-h640dp")
    fun statisticsGridSupportsLargeText() {
        startStatistics(1.8f)
        for (label in listOf("Kierrosta", "Rataa", "Layoutia", "Ace")) {
            val node = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("stats-year"))).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(node.left >= 0 && node.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
        }
    }
    @Test fun multiplePlayersHaveAnOptionalBreakdown() {
        val fake = startStatistics()
        compose.runOnIdle {
            val h = fake.history.value
            fake.history.value = h.copy(players = h.players + h.players.first().copy(id="p2", name="Toinen"),
                entries = h.entries + h.entries.first().copy(playerId="p2"))
        }
        scrollStatisticsTo("Pelaajittain")
        compose.onNodeWithTag("stats-player").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pelaajittain").performClick()
        compose.onAllNodesWithTag("stats-player").assertCountEquals(2)
    }
    @Test @Config(qualifiers = "fi-rFI-w320dp-h640dp")
    fun compactCourseCardsKeepPlayerCountsAndHideUnknownLocation() {
        val fake = Fake()
        runBlocking { fake.loadSample() }
        val original = fake.history.value
        val second = original.players.first().copy(id="p2", name="Toinen pitkä pelaajanimi")
        val third = original.players.first().copy(id="p3", name="Kolmas")
        fake.history.value = original.copy(players=original.players + listOf(second, third),
            courses=original.courses.map { it.copy(name="Testirata (ei käytössä) → Siniset", city=null, country=null,
                latitude=61.5, longitude=23.7, holeCount=18) },
            entries=original.entries + listOf(original.entries.first().copy(playerId="p2"), original.entries.first().copy(playerId="p3")))
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm) }
        compose.onNodeWithText("Testirata → Siniset").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ei käytössä").assertIsDisplayed()
        compose.onNodeWithText("18 väylää").assertIsDisplayed()
        compose.onNodeWithText("Sijainti ei tiedossa").assertDoesNotExist()
        compose.onNodeWithText("Kierroksia: 3").assertDoesNotExist()
        for (name in listOf("Minä (esimerkki) · 1", "Toinen pitkä pelaajanimi · 1", "Kolmas · 1")) {
            val bounds = compose.onNodeWithText(name).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
        }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/screenshots/course-cards.png").apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Testirata → Siniset").performScrollTo().performClick()
        compose.onNodeWithText("Koordinaatit: 61,50000, 23,70000").assertExists()
    }
    @Test fun statisticsShowEventCoverageEvenWhenAllScorecardsAreBlocked() {
        val fake = Fake()
        fake.history.value = History(players = listOf(Player("p", "123", "Oma", 0, true, false, null)),
            metrixEvents = listOf(MetrixEvent("p", "100", true, "HISTORY_LIMIT")))
        val vm = HistoryViewModel(fake, fake, fake)
        compose.setContent { KiekkopolkuApp(vm) }
        compose.onNodeWithText("Tilastot").performClick()
        compose.onNodeWithText("Viimeiset 12 kuukautta").assertIsDisplayed()
        compose.onNodeWithText("Metrix-listan tapahtumatunnisteita: 1").assertDoesNotExist()
        scrollStatisticsTo("Metrix-datan kattavuus")
        compose.onNodeWithContentDescription("Metrix-datan kattavuus").performClick()
        compose.onNodeWithText("Metrix-listan tapahtumatunnisteita: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Vanhan historian rajoittamia tuloshakuja: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Vanhoja käyntejä ilman tuloskorttia: 0. Rajoitettuja tapahtumia ilman varmennettua ratakäyntiä: 1.").performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("Viimeiset 12 kuukautta"))
        compose.onNodeWithText("Viimeiset 12 kuukautta").assertIsDisplayed()
    }
    @Test fun profileCreationAndSelectionEmptyState() {
        start()
        compose.onNodeWithText("Lisää pelaaja").performClick()
        compose.onNodeWithText("Lisää pelaaja").performClick()
        compose.onNodeWithText("Näyttönimi").performTextInput("Testipelaaja")
        compose.onNodeWithText("DiscGolfMetrix-pelaaja-ID").performTextInput("123")
        compose.onNodeWithText("Tallenna").performScrollTo().performClick()
        compose.onNodeWithText("Testipelaaja").assertIsDisplayed()
        compose.onNodeWithContentDescription("Takaisin").performClick()
        compose.onNodeWithText("Kierroksia ei vielä ole").assertIsDisplayed()
        compose.onNodeWithText("Testipelaaja").performClick()
        compose.onNodeWithText("Valitse vähintään yksi pelaaja yläreunasta.").assertIsDisplayed()
    }
    @Test fun playerCanBeAddedWithCodeAndWithoutNameOrId() {
        start()
        compose.onNodeWithText("Lisää pelaaja").performClick()
        compose.onNodeWithText("Lisää pelaaja").performClick()
        compose.onNodeWithText("Metrix-integraatiokoodi").performScrollTo().performTextInput("synthetic-ui-code")
        compose.onNodeWithText("Tallenna").performScrollTo().performClick()
        compose.onNodeWithText("Metrix-pelaaja").assertIsDisplayed()
        compose.onNodeWithText("Pelaaja-ID:tä ei ole annettu").assertIsDisplayed()
        compose.onNodeWithText("Integraatiokoodi liitetty").assertIsDisplayed()
    }
}
