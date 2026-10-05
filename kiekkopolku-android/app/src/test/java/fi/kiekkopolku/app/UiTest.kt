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
        var refreshGate: CompletableDeferred<Unit>? = null
        override val progress = MutableStateFlow<SyncProgress?>(null)
        override suspend fun refreshOnOpen(): RefreshResult {
            opened++
            if (refreshGate != null) {
                progress.value = SyncProgress("Testipelaaja", 1, 3)
                refreshGate!!.await()
                progress.value = null
            }
            return RefreshResult.UPDATED_METRIX
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
        override suspend fun refreshSelected() = RefreshResult.UPDATED_SAMPLE
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
