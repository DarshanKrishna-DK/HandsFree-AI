package com.example.handsfree

import com.example.handsfree.agent.ModelArtifact
import com.example.handsfree.agent.VerifiedModelStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class VerifiedModelStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = "real artifact bytes for integrity tests".toByteArray()
    private fun store(): VerifiedModelStore {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        return VerifiedModelStore(temp.root, ModelArtifact("test", bytes.size.toLong(), hash))
    }
    @Test fun verifiedBytesBecomeAvailableAndDeleteRemovesThem() {
        val store = store()
        assertFalse(store.verify())
        store.import(bytes.inputStream())
        assertTrue(store.verify())
        assertTrue(store.delete())
        assertFalse(store.verify())
    }
    @Test fun rejectsTruncatedOversizedAndTamperedModelsWithoutPromotingPartials() {
        for (invalid in listOf(bytes.dropLast(1).toByteArray(), bytes + 1, ByteArray(bytes.size))) {
            val store = store()
            assertThrows(IllegalArgumentException::class.java) { store.import(invalid.inputStream()) }
            assertFalse(store.modelFile.exists())
            assertTrue(temp.root.listFiles()!!.isEmpty())
        }
    }
    @Test fun cancellationPreservesPriorModelAndClosesInput() {
        val store = store()
        store.import(bytes.inputStream())
        var closed = false
        val source = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
        var calls = 0
        assertThrows(CancellationException::class.java) {
            store.import(source, checkActive = { if (++calls >= 3) throw CancellationException() })
        }
        assertTrue(closed)
        assertTrue(store.verify())
        assertEquals(1, temp.root.listFiles()!!.size)
    }
    @Test fun corruptionAfterImportFailsVerification() {
        val store = store()
        store.import(bytes.inputStream())
        store.modelFile.writeBytes(ByteArray(bytes.size))
        assertFalse(store.verify())
    }
    @Test fun artifactCannotEscapePrivateDirectory() {
        assertThrows(IllegalArgumentException::class.java) { ModelArtifact("../escape", 1, "a".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { ModelArtifact("valid", 1, "../bad") }
    }
}
