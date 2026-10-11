package com.example.handsfree.agent

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

/** Reject malformed model output before the independent action policy sees it. No coercion or repair. */
object ActionProposalCodec {
    private val fields = setOf("action", "target_id", "arguments", "reason", "observation_id", "requires_confirmation")
    private val argumentFields = setOf("package_name", "text", "direction", "message", "expected_text",
        "evidence_ids", "cart_item_ids", "cart_total_ids")

    fun decode(content: String, observationId: String): ProposedAction {
        require(content.length <= 16_384) { "Model response exceeds the size limit" }
        val json = JsonReader(StringReader(content)).use { reader ->
            reader.isLenient = false
            val value = readValue(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing model output" }
            value as? Map<*, *> ?: error("An action must be an object")
        }
        require(json.keys.all { it in fields }) { "Unknown action field" }
        val action = json.string("action", 40, required = true)!!
        require(action in SafetyPolicyEngine.actions) { "Unknown action" }
        val observed = json.string("observation_id", 80, required = true)!!
        require(observed == observationId) { "Stale model response" }
        val confirmation = json["requires_confirmation"]
        require(confirmation is Boolean) { "Confirmation must be a boolean" }
        val args = if (!json.containsKey("arguments")) emptyMap<String, Any?>()
            else json["arguments"] as? Map<*, *> ?: error("Arguments must be an object")
        require(args.keys.all { it in argumentFields }) { "Unknown action argument" }
        val direction = args.string("direction", 8)
        require(direction == null || direction in setOf("forward", "backward")) { "Invalid direction" }
        return ProposedAction(action, observed, json.string("target_id", 80),
            ActionArguments(args.string("package_name", 200), args.string("text", 1000), direction,
                args.string("message", 1000), args.string("expected_text", 500),
                args.ids("evidence_ids", 10), args.ids("cart_item_ids", 10), args.ids("cart_total_ids", 3)),
            json.string("reason", 500, required = true)!!, confirmation)
    }

    private fun Map<*, *>.string(key: String, limit: Int, required: Boolean = false): String? {
        val value = this[key]
        if (value == null) { require(!required) { "Missing $key" }; return null }
        require(value is String && value.length <= limit && (!required || value.isNotBlank())) { "Invalid $key" }
        return value
    }

    private fun Map<*, *>.ids(key: String, limit: Int): List<String> {
        if (!containsKey(key)) return emptyList()
        val values = this[key]
        require(values is List<*> && values.size <= limit) { "Invalid $key" }
        return values.map {
            require(it is String && it.isNotBlank() && it.length <= 80) { "Invalid evidence identifier" }
            it
        }.also { require(it.distinct().size == it.size) { "Duplicate evidence identifier" } }
    }

    private fun readValue(reader: JsonReader, depth: Int): Any? {
        require(depth <= 3) { "Model response is too deeply nested" }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val result = linkedMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(name.length <= 40 && !result.containsKey(name) && result.size < 8) { "Invalid or duplicate field" }
                    result[name] = readValue(reader, depth + 1)
                }
                reader.endObject()
                result
            }
            JsonToken.BEGIN_ARRAY -> {
                val result = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) {
                    require(result.size < 10) { "Too many evidence identifiers" }
                    result += readValue(reader, depth + 1)
                }
                reader.endArray()
                result
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> error("Unexpected model value type")
        }
    }
}
