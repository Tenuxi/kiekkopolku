package fi.kiekkopolku.app

import fi.kiekkopolku.app.data.CredentialCipher
import fi.kiekkopolku.app.data.validateMetrixCodeResponse
import fi.kiekkopolku.app.domain.InvalidIntegrationCodeException
import fi.kiekkopolku.app.domain.MetrixConnectionException
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class CredentialTest {
    @Test fun encryptionUsesFreshIvAndProfileBinding() {
        val cipher = CredentialCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        val first = cipher.encrypt("local-profile", "synthetic-test-code")
        val second = cipher.encrypt("local-profile", "synthetic-test-code")
        assertNotEquals(first, second)
        assertFalse(first.contains("synthetic-test-code"))
        assertEquals("synthetic-test-code", cipher.decrypt("local-profile", first))
        try { cipher.decrypt("another-profile", first); fail("Profile binding must reject the ciphertext") } catch (_: javax.crypto.AEADBadTagException) { }
    }
    @Test fun documentedIdListIncludingEmptyListIsAccepted() {
        validateMetrixCodeResponse("""{"my_competitions":["123",456],"Errors":[]}""")
        validateMetrixCodeResponse("""{"my_competitions":[],"Errors":[]}""")
    }
    @Test fun emptyErrorsWithoutListIsNotAnAcceptedCredential() {
        try { validateMetrixCodeResponse("""{"Errors":[]}"""); fail("Invalid live response must be rejected") }
        catch (_: InvalidIntegrationCodeException) { }
    }
    @Test fun providerErrorsAndHtmlAreRejected() {
        try { validateMetrixCodeResponse("""{"Errors":["invalid code"]}"""); fail("Expected credential error") }
        catch (_: InvalidIntegrationCodeException) { }
        try { validateMetrixCodeResponse("<html>Login</html>"); fail("Expected protocol error") }
        catch (_: MetrixConnectionException) { }
    }
}
