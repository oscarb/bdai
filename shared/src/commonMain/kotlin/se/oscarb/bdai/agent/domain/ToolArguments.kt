package se.oscarb.bdai.agent.domain

/**
 * The arguments of a tool call as the model sent them.
 */
data class ToolArguments(val values: Map<String, Any?>) {

    fun string(name: String): String? {
        val value = values[name]?.toString()?.trim()
        return value?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    fun boolean(name: String): Boolean? = when (val value = values[name]) {
        is Boolean -> value
        is String -> value.trim().toBooleanStrictOrNull()
        else -> null
    }

    fun int(name: String): Int? = when (val value = values[name]) {
        is Number -> value.toInt()
        is String -> value.trim().toDoubleOrNull()?.toInt()
        else -> null
    }
}
