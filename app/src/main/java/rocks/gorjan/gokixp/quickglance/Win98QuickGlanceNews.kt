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
    val publishedAtMs: Long? = null,
    val publishedLabel: String? = null
)

/**
 * User-triggered headline loader for the Windows 98 Quick Glance page.
 *
 * No background polling is performed. The visual Google News page is read only when
 * Quick Glance is opened or the user presses Refresh. That page exposes the same
 * Google-hosted story thumbnails seen in the normal Google News UI, which avoids the
 * image-less RSS-only cards that were previously shown by WIN26.
 */
object Win98QuickGlanceNews {
    private const val HOME_URL =
        "https://news.google.com/home?hl=en-US&gl=US&ceid=US:en"
    private const val FEED_URL =
        "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
    private const val MAX_HOME_HTML_CHARS = 4_000_000

    suspend fun fetchHeadlines(limit: Int = 7): Result<List<Win98NewsItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val visual = loadVisualHeadlines(limit)
                if (visual.size >= minOf(4, limit)) {
                    visual.take(limit)
                } else {
                    val fallback = loadRssHeadlines(limit)
                    (visual + fallback)
                        .distinctBy { it.title.lowercase(Locale.US) }
                        .take(limit)
                }
            }
        }

    private fun loadVisualHeadlines(limit: Int): List<Win98NewsItem> {
        val connection = openConnection(HOME_URL, 9_000, "text/html,application/xhtml+xml")
        try {
            if (connection.responseCode !in 200..299) return emptyList()
            val html = connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(16_384)
                while (out.length < MAX_HOME_HTML_CHARS) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    val remaining = MAX_HOME_HTML_CHARS - out.length
                    out.append(buffer, 0, minOf(read, remaining))
                }
                out.toString()
            }
            return parseVisualCards(html, limit)
        } catch (_: Exception) {
            return emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVisualCards(html: String, limit: Int): List<Win98NewsItem> {
        if (html.isBlank()) return emptyList()

        val items = mutableListOf<Win98NewsItem>()
        var cursor = 0
        var previousArticleEnd = 0

        while (items.size < limit && cursor < html.length) {
            val articleStart = html.indexOf("<article", cursor, ignoreCase = true)
            if (articleStart < 0) break
            val articleEndTag = html.indexOf("</article>", articleStart, ignoreCase = true)
            if (articleEndTag < 0) break
            val articleEnd = articleEndTag + "</article>".length
            val article = html.substring(articleStart, articleEnd)

            val headingHtml = firstMatch(
                article,
                listOf(
                    Regex("""(?is)<h3\b[^>]*>(.*?)</h3>"""),
                    Regex("""(?is)<h4\b[^>]*>(.*?)</h4>""")
                )
            )
            val title = headingHtml?.let(::cleanHtmlText).orEmpty()

            if (title.length >= 12) {
                val href = headingHtml?.let {
                    Regex("""(?is)href\s*=\s*["']([^"']+)["']""")
                        .find(it)?.groupValues?.getOrNull(1)
                } ?: Regex("""(?is)href\s*=\s*["']([^"']+)["']""")
                    .find(article)?.groupValues?.getOrNull(1)

                val storyUrl = href?.let(::normalizeGoogleNewsUrl)

                if (storyUrl != null) {
                    val sourceHtml = Regex(
                        """(?is)<a\b[^>]*data-n-tid[^>]*>(.*?)</a>"""
                    ).find(article)?.groupValues?.getOrNull(1)
                    val source = sourceHtml?.let(::cleanHtmlText)
                        ?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
                        ?: "Google News"

                    val publishedLabel = Regex(
                        """(?is)<time\b[^>]*>(.*?)</time>"""
                    ).find(article)?.groupValues?.getOrNull(1)
                        ?.let(::cleanHtmlText)
                        ?.takeIf { it.isNotBlank() }

                    // Google places the thumbnail in a sibling immediately before the
                    // <article>, so include a bounded chunk of preceding card markup.
                    val imageContextStart = maxOf(previousArticleEnd, articleStart - 3500)
                    val imageContext = html.substring(imageContextStart, articleEnd)
                    val imageUrl = Regex(
                        """(?is)<img\b[^>]*(?:src|data-src)\s*=\s*["']([^"']+)["'][^>]*>"""
                    ).findAll(imageContext)
                        .mapNotNull { it.groupValues.getOrNull(1) }
                        .map(::decodeHtml)
                        .filter { it.startsWith("https://") || it.startsWith("http://") }
                        .lastOrNull()

                    items += Win98NewsItem(
                        title = title,
                        source = source,
                        url = storyUrl,
                        imageUrl = imageUrl,
                        publishedLabel = publishedLabel
                    )
                }
            }

            previousArticleEnd = articleEnd
            cursor = articleEnd
        }

        return items.distinctBy { it.title.lowercase(Locale.US) }
    }

    private fun firstMatch(text: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val value = pattern.find(text)?.groupValues?.getOrNull(1)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun cleanHtmlText(value: String): String {
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodeHtml(value: String): String {
        return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace("&amp;", "&")
            .trim()
    }

    private fun normalizeGoogleNewsUrl(raw: String): String? {
        val href = decodeHtml(raw)
        return when {
            href.startsWith("https://") || href.startsWith("http://") -> href
            href.startsWith("./") -> "https://news.google.com/" + href.removePrefix("./")
            href.startsWith("/") -> "https://news.google.com$href"
            else -> null
        }
    }

    private fun loadRssHeadlines(limit: Int): List<Win98NewsItem> {
        val connection = openConnection(
            FEED_URL,
            8_000,
            "application/rss+xml,application/xml,text/xml"
        )
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Headline request failed with HTTP $code")
            }

            return connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(input, "UTF-8")
                }

                val items = mutableListOf<Win98NewsItem>()
                var inItem = false
                var title = ""
                var link = ""
                var source = ""
                var publishedAtMs: Long? = null

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                            "item" -> {
                                inItem = true
                                title = ""
                                link = ""
                                source = ""
                                publishedAtMs = null
                            }
                            "title" -> if (inItem) title = parser.nextText().trim()
                            "link" -> if (inItem) link = parser.nextText().trim()
                            "source" -> if (inItem) source = parser.nextText().trim()
                            "pubdate" -> if (inItem) {
                                publishedAtMs = parseRssDate(parser.nextText())
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
                                items += Win98NewsItem(
                                    title = cleanTitle,
                                    source = resolvedSource.ifBlank { "Google News" },
                                    url = link,
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

    private fun openConnection(
        url: String,
        timeoutMs: Int,
        accept: String
    ): HttpURLConnection {
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
            setRequestProperty("Accept", accept)
        }
    }
}
