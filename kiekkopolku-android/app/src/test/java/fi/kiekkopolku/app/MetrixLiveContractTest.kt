package fi.kiekkopolku.app

import fi.kiekkopolku.app.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Optional read-only contract check; public responses stay outside Git and the APK. No credentials. */
class MetrixLiveContractTest {
    @Test fun downloadedPublicResponseMapsWithoutPersistingPersonalData() {
        val path = System.getenv("METRIX_PUBLIC_RESULT_FIXTURE")
        assumeTrue("Set METRIX_PUBLIC_RESULT_FIXTURE to a downloaded public result JSON", path != null)
        val root = Json.parseToJsonElement(File(path!!).readText()).jsonObject
        val c = root.getValue("Competition").jsonObject
        val result = c.getValue("Results").jsonArray.first().jsonObject
        val player = PlayerEntity("local-test", result.getValue("UserID").jsonPrimitive.content, "Test")
        val parsed = parseMetrixResult(root, c.getValue("ID").jsonPrimitive.content, player, 0)
        assertEquals(result.getValue("Sum").jsonPrimitive.int, parsed.batch.entries.single().totalScore)
        assertEquals(c.getValue("Tracks").jsonArray.size, parsed.batch.holes.size)
        assertTrue(parsed.batch.entries.single().holeDataComplete)
        assertEquals(result.getValue("PlayerResults").jsonArray.map { it.jsonObject.getValue("Result").jsonPrimitive.int },
            parsed.batch.holes.map { it.score })
    }
}
