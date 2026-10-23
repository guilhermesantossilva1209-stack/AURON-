package br.com.auron.cloud

import br.com.auron.auth.ConnectionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudPolicyTest {
    @Test fun payloadLimitIsMeasuredInBytesAndDoesNotTruncateRecords() {
        CloudPolicy.requirePayloadSize(1)
        CloudPolicy.requirePayloadSize(1_048_576)
        listOf(-1, 0, 1_048_577).forEach { count ->
            try { CloudPolicy.requirePayloadSize(count); fail("Must reject invalid size") }
            catch (failure: CloudFailure) { assertEquals(CloudFailureKind.TOO_LARGE, failure.kind) }
        }
        val unicode = "á".repeat(524_289)
        assertTrue(unicode.length < CloudPolicy.MAX_PAYLOAD_BYTES)
        try { CloudPolicy.requirePayloadSize(unicode.toByteArray(Charsets.UTF_8).size); fail("UTF-8 limit") }
        catch (failure: CloudFailure) { assertEquals(CloudFailureKind.TOO_LARGE, failure.kind) }
    }

    @Test fun zeroRevisionAllowsInitialCreationButNeverDeletion() {
        CloudPolicy.requireRevision(0)
        CloudPolicy.requireRevision(1, deleting = true)
        try { CloudPolicy.requireRevision(0, deleting = true); fail("No unseen deletion") }
        catch (_: IllegalArgumentException) { }
        try { CloudPolicy.requireRevision(-1); fail("No negative revision") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun insecureConfigIsRejectedWithoutObtainingSession() = runBlocking {
        var requests = 0
        for (url in listOf("http://example.invalid", "https://user:pass@example.invalid", "https://example.invalid/path")) {
            val client = SupabaseCloudClient(ConnectionConfig(url, PUBLIC_KEY)) { requests++; "TOKEN" }
            try { client.fetch(); fail("Invalid config") }
            catch (failure: CloudFailure) { assertEquals(CloudFailureKind.CONFIGURATION, failure.kind) }
        }
        assertEquals(0, requests)
    }

    @Test fun missingSessionStopsBeforeNetworkAndDoesNotLeakProviderErrors() = runBlocking {
        val config = ConnectionConfig("https://example.invalid", PUBLIC_KEY)
        for (provider in listOf<suspend () -> String?>({ null }, { error("PRIVATE_TEST_CREDENTIAL") })) {
            try { SupabaseCloudClient(config, provider).fetch(); fail("Missing session") }
            catch (failure: CloudFailure) {
                assertEquals(CloudFailureKind.SESSION, failure.kind)
                assertFalse(failure.safeMessage.contains("PRIVATE_TEST_CREDENTIAL"))
            }
        }
    }

    @Test fun profileCancellationPropagates() = runBlocking {
        val client = SupabaseCloudClient(ConnectionConfig("https://example.invalid", PUBLIC_KEY)) { throw CancellationException("Profile changed") }
        try { client.fetch(); fail("Must cancel") }
        catch (_: CancellationException) { }
    }

    private companion object {
        const val PUBLIC_KEY = "sb_publishable_placeholder_for_offline_tests"
    }
}
