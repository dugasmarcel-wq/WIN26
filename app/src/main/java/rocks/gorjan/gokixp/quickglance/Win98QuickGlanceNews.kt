package rocks.gorjan.gokixp.quickglance

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val publishedLabel: String? = null,
    val topic: String = "World"
)

/**
 * On-demand Quick Glance news: gaming, world affairs and conflict reporting.
 *
 * Pulls only direct RSS from a short allowlist of free-to-read publishers. Each
 * story keeps its publisher attribution and URL; no Google News paywall aggregation
 * or background refresh. Images prefer RSS media, then publisher OG metadata.
 */
object Win98QuickGlanceNews {
    // Only free-access, direct-publisher RSS links. No paywalled Google News
    // aggregation, tracking, subscription feeds, or background fetching.
    private const val GAME_FEED = "https://www.gematsu.com/feed"
    private const val GAME_BACKUP = "https://www.eurogamer.net/feed/news"
    private const val GAME_EXTRA = "https://www.gamingonlinux.com/article_rss.php?newsonly"
    private const val WORLD_FEED = "https://rss.dw.com/xml/rss-en-world"
    private const val WORLD_BACKUP = "https://www.france24.com/en/rss"
    private const val CONFLICT_FEED = "https://www.aljazeera.com/xml/rss/all.xml"
    private const val MAX_ARTICLE_HTML_CHARS = 420_000
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    private data class NewsFeed(val url: String, val source: String, val topic: String)
    private val feeds = listOf(
        NewsFeed(GAME_FEED, "Gematsu", "Gaming"),
        NewsFeed(GAME_BACKUP, "Eurogamer", "Gaming"),
        NewsFeed(GAME_EXTRA, "GamingOnLinux", "Gaming"),
        NewsFeed(WORLD_FEED, "Deutsche Welle", "World"),
        NewsFeed(WORLD_BACKUP, "France 24", "World"),
        NewsFeed(CONFLICT_FEED, "Al Jazeera", "Conflicts")
    )

    private val conflictKeywords = Regex(
        """(?i)\b(war|wars|warfare|conflict|military|troops|invasion|airstrike|missile|"""
        + """shelling|frontline|offensive|ceasefire|peace talks|hostage|gaza|ukraine|"""
        + """russia|israel|iran|sudan|yemen|attack|drone strike|sanctions|"""
        + """peace deal|armed forces|border clash|occupation|insurgent)\b"""
    )

    @Volatile
    private var cachedHeadlines: List<Win98NewsItem> = emptyList()
    @Volatile
    private var cachedAtMs: Long = 0L

    private fun openAccessStory(item: Win98NewsItem): Boolean {
        val host = runCatching { URL(item.url).host.lowercase(Locale.US) }.getOrNull()
            ?: return false
        val approved = listOf("gematsu.com", "gamingonlinux.com",
            "eurogamer.net", "dw.com", "france24.com", "aljazeera.com")
        if (approved.none { host == it || host.endsWith(".$it") }) return false
        // Reject explicitly gated articles even from ordinarily free publishers.
        val path = runCatching { URL(item.url).path.lowercase(Locale.US) }.getOrDefault("")
        if (listOf("/premium/", "/subscriber/", "/subscribe/", "/members/",
            "/supporter/", "/paid/").any { it in path }) return false
        val title = item.title.lowercase(Locale.US)
        return listOf("subscribers only", "subscriber-only", "members only",
            "member-only", "premium exclusive", "subscription required",
            "sign in to read", "premium members").none { it in title }
    }

    private fun rankStories(stories: List<Win98NewsItem>): List<Win98NewsItem> {
        return stories.filter(::openAccessStory)
            .distinctBy { normalizedTitle(it.title) }
            .sortedWith(compareByDescending<Win98NewsItem> {
                it.publishedAtMs?.div(3_600_000L) ?: 0L
            }.thenByDescending {
                if (it.imageUrl.isNullOrBlank()) 0 else 1
            }.thenByDescending { it.publishedAtMs ?: 0L })
    }

    private fun pickCuratedStories(all: List<Win98NewsItem>, limit: Int): List<Win98NewsItem> {
        val gaming = rankStories(all.filter { it.topic == "Gaming" })
        val conflict = rankStories(all.filter {
            it.topic != "Gaming" && conflictKeywords.containsMatchIn(it.title)
        }).map { it.copy(topic = "Conflicts") }
        val world = rankStories(all.filter {
            it.topic != "Gaming" && !conflictKeywords.containsMatchIn(it.title)
        }).map { it.copy(topic = "World") }
        val groups = listOf(gaming, world, conflict)
        val chosen = linkedMapOf<String, Win98NewsItem>()
        // Rotate categories so the page does not become a wall of a single outlet.
        for (index in 0 until maxOf(gaming.size, world.size, conflict.size)) {
            for (group in groups) {
                group.getOrNull(index)?.let { chosen.putIfAbsent(normalizedTitle(it.title), it) }
                if (chosen.size >= limit) return chosen.values.toList()
            }
        }
        return chosen.values.toList().take(limit)
    }

    suspend fun fetchHeadlines(
        limit: Int = 9,
        forceRefresh: Boolean = false
    ): Result<List<Win98NewsItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cachedHeadlines.isNotEmpty() &&
                now - cachedAtMs < CACHE_TTL_MS
            ) return@runCatching cachedHeadlines.take(limit)

            // These requests run only after entering Quick Glance or pressing Refresh,
            // never during page prewarming or in a persistent background service.
            val result = coroutineScope {
                val requests = feeds.map { feed ->
                    async {
                        runCatching {
                            loadRssHeadlines(16, feed.url, feed.source)
                                .map { it.copy(topic = feed.topic) }
                        }.getOrDefault(emptyList())
                    }
                }
                pickCuratedStories(requests.awaitAll().flatten(), limit)
            }
            if (result.isNotEmpty()) {
                cachedHeadlines = result
                cachedAtMs = now
            }
            result.ifEmpty {
                cachedHeadlines.takeIf { it.isNotEmpty() } ?: emptyList()
            }
        }
    }

    suspend fun fetchImagesForHeadlines(
        headlines: List<Win98NewsItem>
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                val candidates = headlines.filter { it.imageUrl.isNullOrBlank() }.take(5)
                val result = candidates.map { item ->
                    async { item.url to findPublisherImage(item.url) }
                }.awaitAll()
                    .mapNotNull { (url, photo) ->
                        photo?.takeIf(::looksLikeUsableImage)?.let { url to it }
                    }.toMap()
                if (result.isNotEmpty()) {
                    cachedHeadlines = headlines.map { item ->
                        item.copy(imageUrl = result[item.url] ?: item.imageUrl)
                    }
                }
                result
            }
        }
    }

    private fun findPublisherImage(storyUrl: String): String? {
        val firstPage = fetchHtml(storyUrl, 2_800, MAX_ARTICLE_HTML_CHARS) ?: return null
        val direct = extractPageImage(firstPage.html, firstPage.finalUrl)
        if (direct != null && looksLikeUsableImage(direct)) return direct

        // Google News article URLs sometimes return an intermediate page instead of a
        // normal HTTP redirect. Try a few external publisher links from that page.
        if (isGoogleNewsUrl(firstPage.finalUrl)) {
            extractExternalLinks(firstPage.html, firstPage.finalUrl)
                .take(3)
                .forEach { publisherUrl ->
                    val publisherPage = fetchHtml(
                        publisherUrl,
                        2_500,
                        MAX_ARTICLE_HTML_CHARS
                    ) ?: return@forEach
                    val image = extractPageImage(publisherPage.html, publisherPage.finalUrl)
                    if (image != null && looksLikeUsableImage(image)) return image
                }
        }
        return null
    }

    private data class HtmlPage(
        val finalUrl: String,
        val html: String
    )

    private fun fetchHtml(url: String, timeoutMs: Int, maxChars: Int): HtmlPage? {
        val connection = openConnection(url, timeoutMs, "text/html,application/xhtml+xml")
        return try {
            val code = connection.responseCode
            if (code !in 200..399) return null
            val html = connection.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(12_288)
                while (out.length < maxChars) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    val remaining = maxChars - out.length
                    out.append(buffer, 0, minOf(read, remaining))
                }
                out.toString()
            }
            HtmlPage(connection.url.toString(), html)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun extractPageImage(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null

        val metaTags = Regex("""(?is)<meta\b[^>]*>""").findAll(html)
        for (match in metaTags) {
            val tag = match.value
            val property = attributeValue(tag, "property")
                ?: attributeValue(tag, "name")
                ?: continue
            if (
                property.equals("og:image", true) ||
                property.equals("og:image:url", true) ||
                property.equals("twitter:image", true) ||
                property.equals("twitter:image:src", true)
            ) {
                val content = attributeValue(tag, "content") ?: continue
                resolveUrl(baseUrl, content)?.let { if (looksLikeUsableImage(it)) return it }
            }
        }

        val linkTags = Regex("""(?is)<link\b[^>]*>""").findAll(html)
        for (match in linkTags) {
            val tag = match.value
            val rel = attributeValue(tag, "rel") ?: continue
            if (rel.contains("image_src", ignoreCase = true)) {
                val href = attributeValue(tag, "href") ?: continue
                resolveUrl(baseUrl, href)?.let { if (looksLikeUsableImage(it)) return it }
            }
        }
        return null
    }

    private fun attributeValue(tag: String, name: String): String? {
        val escaped = Regex.escape(name)
        return Regex("""(?is)\b$escaped\s*=\s*["']([^"']+)["']""")
            .find(tag)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeHtml)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractExternalLinks(html: String, baseUrl: String): List<String> {
        val links = linkedSetOf<String>()
        Regex("""(?is)href\s*=\s*["']([^"']+)["']""")
            .findAll(html)
            .forEach { match ->
                val raw = match.groupValues.getOrNull(1) ?: return@forEach
                val resolved = resolveUrl(baseUrl, raw) ?: return@forEach
                val host = runCatching { URL(resolved).host.lowercase(Locale.US) }.getOrNull()
                    ?: return@forEach
                if (
                    host.isNotBlank() &&
                    !host.endsWith("google.com") &&
                    !host.endsWith("googleusercontent.com") &&
                    !host.endsWith("gstatic.com") &&
                    !host.endsWith("youtube.com")
                ) {
                    links.add(resolved)
                }
            }
        return links.toList()
    }

    private fun normalizedTitle(value: String): String =
        cleanHtmlText(value)
            .lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    private fun extractVisualImageUrl(html: String, baseUrl: String): String? {
        val imageTags = Regex("""(?is)<img\b[^>]*>""")
            .findAll(html)
            .map { it.value }
            .toList()
            .asReversed()

        imageTags.forEach { tag ->
            val directCandidates = listOf("data-src", "src").mapNotNull { attribute ->
                attributeValue(tag, attribute)
            }
            for (raw in directCandidates) {
                normalizeImageUrl(raw, baseUrl)?.let { if (looksLikeUsableImage(it)) return it }
            }

            val srcset = attributeValue(tag, "srcset")
            if (!srcset.isNullOrBlank()) {
                srcset.split(',')
                    .asReversed()
                    .map { it.trim().substringBefore(' ') }
                    .forEach { raw ->
                        normalizeImageUrl(raw, baseUrl)?.let {
                            if (looksLikeUsableImage(it)) return it
                        }
                    }
            }
        }

        val background = Regex(
            """(?is)background-image\s*:\s*url\(\s*['"]?([^)'"]+)"""
        ).findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .toList()
            .asReversed()
        for (raw in background) {
            normalizeImageUrl(raw, baseUrl)?.let { if (looksLikeUsableImage(it)) return it }
        }
        return null
    }

    private fun normalizeImageUrl(raw: String, baseUrl: String): String? {
        val value = decodeHtml(raw).trim()
        if (value.isBlank() || value.startsWith("data:", ignoreCase = true)) return null
        return resolveUrl(baseUrl, value)
    }

    private fun resolveUrl(baseUrl: String, raw: String): String? {
        val value = decodeHtml(raw).trim()
        if (value.isBlank() || value.startsWith("data:", true)) return null
        return runCatching { URL(URL(baseUrl), value).toString() }.getOrNull()
    }

    private fun looksLikeUsableImage(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        if (
            "favicon" in lower ||
            "sprite" in lower ||
            "icon-" in lower ||
            "/logo" in lower ||
            "googlelogo" in lower
        ) return false
        return true
    }

    private fun isGoogleNewsUrl(url: String): Boolean =
        runCatching { URL(url).host.equals("news.google.com", ignoreCase = true) }
            .getOrDefault(false)

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

    private fun loadRssHeadlines(
        limit: Int,
        feedUrl: String,
        defaultSource: String
    ): List<Win98NewsItem> {
        val connection = openConnection(
            feedUrl,
            4_000,
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
                var imageUrl: String? = null
                var publishedAtMs: Long? = null

                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val tag = parser.name.lowercase(Locale.US)
                            when (tag) {
                                "item" -> {
                                    inItem = true
                                    title = ""
                                    link = ""
                                    source = ""
                                    imageUrl = null
                                    publishedAtMs = null
                                }
                                "title" -> if (inItem) title = parser.nextText().trim()
                                "link" -> if (inItem) link = parser.nextText().trim()
                                "source" -> if (inItem) source = parser.nextText().trim()
                                "pubdate" -> if (inItem) {
                                    publishedAtMs = parseRssDate(parser.nextText())
                                }
                                "description", "content:encoded" -> if (inItem) {
                                    val html = parser.nextText()
                                    if (imageUrl.isNullOrBlank()) {
                                        imageUrl = extractVisualImageUrl(
                                            html, link.ifBlank { feedUrl }
                                        )
                                    }
                                }
                                "media:content", "media:thumbnail", "enclosure",
                                "thumbnail" -> if (inItem) {
                                    val candidate = parser.getAttributeValue(null, "url")
                                    val medium = parser.getAttributeValue(null, "medium")
                                    val type = parser.getAttributeValue(null, "type")
                                    val isImage = medium.equals("image", ignoreCase = true) ||
                                        type?.startsWith("image/", ignoreCase = true) == true ||
                                        tag == "media:thumbnail" || tag == "thumbnail"
                                    if (!candidate.isNullOrBlank() &&
                                        (isImage || tag == "media:content" || tag == "enclosure") &&
                                        looksLikeUsableImage(candidate)
                                    ) {
                                        // Prefer the thumbnail over a surrounding media
                                        // enclosure. Publisher feeds frequently supply media:thumbnail per article.
                                        if (imageUrl == null || tag.endsWith("thumbnail")) {
                                            imageUrl = candidate
                                        }
                                    }
                                }
                            }
                        }

                        XmlPullParser.END_TAG -> if (
                            parser.name.equals("item", ignoreCase = true) && inItem
                        ) {
                            if (title.isNotBlank() &&
                                (link.startsWith("https://") || link.startsWith("http://"))) {
                                val resolvedSource = source.ifBlank {
                                    title.substringAfterLast(" - ", defaultSource).trim()
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
                                    source = resolvedSource.ifBlank { defaultSource },
                                    url = link,
                                    imageUrl = imageUrl,
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
                // Try next common RSS date form.
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
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
    }
}
