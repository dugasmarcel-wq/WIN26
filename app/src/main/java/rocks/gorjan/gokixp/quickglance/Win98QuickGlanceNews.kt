package rocks.gorjan.gokixp.quickglance

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class Win98NewsItem(
    val title: String,
    val source: String,
    val url: String,
    val imageUrl: String? = null,
    val publishedAtMs: Long? = null
)

/**
 * User-triggered headline loader for the Windows 98 Quick Glance page.
 *
 * No background polling is performed. A request is made only when Quick Glance is opened
 * or when the user presses Refresh. Story images are resolved in that same foreground load.
 */
object Win98QuickGlanceNews {
    private const val FEED_URL =
        "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
    private const val MAX_HTML_CHARS = 350_000

    private data class RawItem(
        val title: String,
        val source: String,
        val url: String,
        val description: String,
        val imageUrl: String?,
        val publishedAtMs: Long?
    )

    suspend fun fetchHeadlines(limit: Int = 7): Result<List<Win98NewsItem>> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(loadHeadlines(limit))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun loadHeadlines(limit: Int): List<Win98NewsItem> {
        val connection = openConnection(FEED_URL, 8_000)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Headline request failed with HTTP $code")
            }

            val rawItems = connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(input, "UTF-8")
                }

                val items = mutableListOf<RawItem>()
                var inItem = false
                var title = ""
                var link = ""
                var source = ""
                var description = ""
                var imageUrl: String? = null
                var publishedAtMs: Long? = null

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name.lowercase(Locale.US)
                            when {
                                tag == "item" -> {
                                    inItem = true
                                    title = ""
                                    link = ""
                                    source = ""
                                    description = ""
                                    imageUrl = null
                                    publishedAtMs = null
                                }
                                inItem && tag == "title" -> title = parser.nextText().trim()
                                inItem && tag == "link" -> link = parser.nextText().trim()
                                inItem && tag == "source" -> source = parser.nextText().trim()
                                inItem && tag == "description" -> description = parser.nextText()
                                inItem && tag == "pubdate" -> {
                                    publishedAtMs = parseRssDate(parser.nextText())
                                }
                                inItem && (
                                    tag.endsWith("thumbnail") ||
                                        tag.endsWith("content") ||
                                        tag == "enclosure"
                                    ) -> {
                                    val candidate = parser.getAttributeValue(null, "url")
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (
                                        !candidate.isNullOrBlank() &&
                                        (type.isBlank() || type.startsWith("image/"))
                                    ) {
                                        imageUrl = candidate
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> if (
                            parser.name.equals("item", ignoreCase = true) && inItem
                        ) {
                            if (title.isNotBlank() && link.startsWith("https://")) {
                                val resolvedSource = source.ifBlank {
                                    title.substringAfterLast(" - ", "Google News").trim()
                                }
                                val cleanTitle = if (
                                    resolvedSource.isNotBlank() &&
                                    title.endsWith(" - $resolvedSource")
                                ) {
                                    title.removeSuffix(" - $resolvedSource").trim()
                                } else {
                                    title
                                }
                                items += RawItem(
                                    title = cleanTitle,
                                    source = resolvedSource.ifBlank { "Google News" },
                                    url = link,
                                    description = description,
                                    imageUrl = imageUrl ?: findImageInHtml(description),
                                    publishedAtMs = publishedAtMs
                                )
                            }
                            inItem = false
                        }
                    }
                    event = parser.next()
                }
                items
            }

            if (rawItems.isEmpty()) {
                throw IllegalStateException("Headline feed contained no stories")
            }

            return rawItems.map { item ->
                Win98NewsItem(
                    title = item.title,
                    source = item.source,
                    url = item.url,
                    imageUrl = item.imageUrl ?: resolveOpenGraphImage(item.url),
                    publishedAtMs = item.publishedAtMs
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRssDate(value: String): Long? {
        val patterns = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z"
        )
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(value.trim())?.time
            } catch (_: Exception) {
                // Try the next common RSS date form.
            }
        }
        return null
    }

    private fun resolveOpenGraphImage(articleUrl: String): String? {
        return try {
            val connection = openConnection(articleUrl, 5_000)
            try {
                if (connection.responseCode !in 200..299) return null
                val html = connection.inputStream.bufferedReader().use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8_192)
                    while (out.length < MAX_HTML_CHARS) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        val remaining = MAX_HTML_CHARS - out.length
                        out.append(buffer, 0, minOf(read, remaining))
                    }
                    out.toString()
                }
                findImageInHtml(html)
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findImageInHtml(html: String): String? {
        if (html.isBlank()) return null

        val patterns = listOf(
            Regex(
                """<meta[^>]+(?:property|name)\s*=\s*["'](?:og:image|twitter:image(?::src)?)["'][^>]+content\s*=\s*["']([^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+(?:property|name)\s*=\s*["'](?:og:image|twitter:image(?::src)?)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<img[^>]+src\s*=\s*["'](https?://[^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in patterns) {
            val raw = pattern.find(html)?.groupValues?.getOrNull(1) ?: continue
            val decoded = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace("&amp;", "&")
                .trim()
            if (decoded.startsWith("https://") || decoded.startsWith("http://")) {
                return decoded
            }
        }
        return null
    }

    private fun openConnection(url: String, timeoutMs: Int): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
            setRequestProperty(
                "Accept",
                "application/rss+xml, application/xml, text/xml, text/html,application/xhtml+xml"
            )
        }
    }
}
