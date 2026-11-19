package player.vvvion.app.data.parse

data class M3uHeader(
    val epgUrls: List<String>,
    val catchup: String?,
    val catchupDays: Int?,
)

data class ParsedChannel(
    val name: String,
    val url: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val group: String,
    val number: Int?,
    val catchup: String?,
    val catchupDays: Int?,
    val catchupSource: String?,
)

data class ParsedProgram(
    val channelId: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val stopUtc: Long,
)
