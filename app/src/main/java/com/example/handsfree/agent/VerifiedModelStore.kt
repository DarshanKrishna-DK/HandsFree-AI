package com.example.handsfree.agent

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Immutable artifact identity. Model weights are never part of the APK or repository. */
data class ModelArtifact(val id: String, val bytes: Long, val sha256: String) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        require(bytes > 0)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
    }
    val fileName get() = "$id-$sha256.litertlm"

    companion object {
        // Public model revision b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1, LFS SHA-256.
        val GEMMA = ModelArtifact("gemma-4-E2B-it", 2_588_147_712L,
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c")
        const val SOURCE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm"
    }
}

/** Caller owns thread/coroutine dispatch. Cancellation is checked every buffer and before promotion. */
class VerifiedModelStore(private val directory: File, val artifact: ModelArtifact) {
    val modelFile get() = File(directory, artifact.fileName)

    fun verify(checkActive: () -> Unit = {}): Boolean {
        val file = modelFile
        if (!file.isFile || file.length() != artifact.bytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { source ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                checkActive()
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        checkActive()
        return digest.hex() == artifact.sha256
    }

    @Synchronized
    fun import(source: InputStream, checkActive: () -> Unit = {}, progress: (Long) -> Unit = {}): File {
        source.use { input ->
            checkActive()
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create private model storage" }
            require(directory.usableSpace >= artifact.bytes + 64L * 1024 * 1024) { "Not enough free storage" }
            val staging = File.createTempFile("model-", ".partial", directory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                staging.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= artifact.bytes) { "Model file is larger than the supported artifact" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        progress(total)
                    }
                    output.fd.sync()
                }
                require(total == artifact.bytes) { "Model file is incomplete" }
                require(digest.hex() == artifact.sha256) { "Model integrity check failed" }
                checkActive()
                // A valid existing artifact is never overwritten. New identities use new paths.
                if (modelFile.exists()) {
                    check(verify(checkActive)) { "Existing model is damaged; remove it before importing again" }
                    return modelFile
                }
                check(staging.renameTo(modelFile)) { "Could not activate the verified model" }
                return modelFile
            } finally { staging.delete() }
        }
    }

    @Synchronized
    fun delete(): Boolean = !modelFile.exists() || modelFile.delete()

    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
