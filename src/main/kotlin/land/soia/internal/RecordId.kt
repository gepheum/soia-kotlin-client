internal data class RecordId(
    val recordId: String,
    val modulePath: String,
    val name: String,
    val qualifiedName: String,
    val nameParts: List<String>,
) {
    companion object {
        fun parse(recordId: String): RecordId {
            val colonIndex = recordId.lastIndexOf(":")
            val modulePath = recordId.substring(0, colonIndex)
            val qualifiedName = recordId.substring(colonIndex + 1)
            val nameParts = qualifiedName.split(".")
            return RecordId(
                recordId = recordId,
                modulePath = modulePath,
                name = nameParts.last(),
                qualifiedName = qualifiedName,
                nameParts = nameParts,
            )
        }
    }
}
