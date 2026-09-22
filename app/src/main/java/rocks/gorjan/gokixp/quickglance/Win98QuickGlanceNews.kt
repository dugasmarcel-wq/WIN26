package rocks.gorjan.gokixp.quickglance

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

data class Win98NewsItem(
    val title: String,
    val source: String,
    val url: String
)

/**
 * User-triggered headline loader for the Windows 98 Quick Glance page.
 *
 * No background polling is performed. A request is made only when Quick Glance is opened
 * or when the user presses Refresh.
 */
object Win98QuickGlanceNews {
    private const val FEED_URL =
        "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"

    suspend fun fetchHeadlines(limit: Int = 6): Result<List<Win98NewsItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(FEED_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "WIN26 Quick Glance")
                    setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
                }

                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("Headline request failed with HTTP $code")
                    }

                    connection.inputStream.use { input ->
                        val parser = Xml.newPullParser().apply {
                            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                            setInput(input, "UTF-8")
                        }

                        val items = mutableListOf<Win98NewsItem>()
                        var inItem = false
                        var title = ""
                        var link = ""
                        var source = ""

                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                            when (event) {
                                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                                    "item" -> {
                                        inItem = true
                                        title = ""
                                        link = ""
                                        source = ""
                                    }
                                    "title" -> if (inItem) title = parser.nextText().trim()
                                    "link" -> if (inItem) link = parser.nextText().trim()
                                    "source" -> if (inItem) source = parser.nextText().trim()
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
                                            url = link
                                        )
                                    }
                                    inItem = false
                                }
                            }
                            event = parser.next()
                        }

                        if (items.isEmpty()) {
                            throw IllegalStateException("Headline feed contained no stories")
                        }
                        items
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
}
