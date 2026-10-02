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
    val publishedLabel: String? = null
)

/**
 * User-triggered Quick Glance news loader.
 *
 * Headlines come from the small Google News RSS feed first. Images are discovered separately
 * from RSS media fields, the resolved publisher page's og:image/twitter:image metadata, and
 * finally the Google News visual page. This keeps headlines fast without accepting blank cards.
 */
object Win98QuickGlanceNews {
    private const val HOME_URL =
        "https://news.google.com/home?hl=en-US&gl=US&ceid=US:en"
    private const val FEED_URL =
        "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
    private const val MAX_HOME_HTML_CHARS = 1_500_000
    private const val MAX_ARTICLE_HTML_CHARS = 420_000
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    @Volatile
    private var cachedHeadlines: List<Win98NewsItem> = emptyList()

    @Volatile
    private var cachedAtMs: Long = 0L

    suspend fun fetchHeadlines(
        limit: Int = 7,
        forceRefresh: Boolean = false
    ): Result<List<Win98NewsItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val cached = cachedHeadlines
            if (!forceRefresh && cached.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) {
                return@runCatching cached.take(limit)
            }

            val rss = loadRssHeadlines(limit)
            val result = if (rss.isNotEmpty()) rss else loadVisualHeadlines(limit)
            cachedHeadlines = result
            cachedAtMs = now
            result
        }
    }

    suspend fun fetchImagesForHeadlines(
        headlines: List<Win98NewsItem>
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            if (headlines.isEmpty()) return@runCatching emptyMap()

            coroutineScope {
                // Publisher metadata requests run in parallel, so one slow publisher does not
                // serialize seven 2-3 second waits.
                val publisherImagesDeferred = headlines.take(7).map { item ->
                    async {
                        item.url to (
                            item.imageUrl?.takeIf(::looksLikeUsableImage)
                                ?: findPublisherImage(item.url)
                        )
                    }
                }

                // Google visual cards are only a fallback and run at the same time.
                val visualDeferred = async {
                    loadVisualHeadlines(maxOf(headlines.size, 12))
                }

                val publisherImages = publisherImagesDeferred.awaitAll().toMap()
                val visual = visualDeferred.await()

                val images = linkedMapOf<String, String>()
                headlines.forEach { headline ->
                    val image = publisherImages[headline.url]
                        ?: findVisualMatch(headline, visual)?.imageUrl
                    if (!image.isNullOrBlank() && looksLikeUsableImage(image)) {
                        images[headline.url] = image
                    }
                }

                if (images.isNotEmpty()) {
                    cachedHeadlines = headlines.map { item ->
                        item.copy(imageUrl = images[item.url] ?: item.imageUrl)
                    }
                    cachedAtMs = System.currentTimeMillis()
                }
                images
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

    private fun loadVisualHeadlines(limit: Int): List<Win98NewsItem> {
        val page = fetchHtml(HOME_URL, 4_000, MAX_HOME_HTML_CHARS) ?: return emptyList()
        return parseVisualCards(page.html, limit)
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

                    val imageContextStart = maxOf(previousArticleEnd, articleStart - 7000)
                    val imageContextEnd = minOf(html.length, articleEnd + 3000)
                    val imageContext = html.substring(imageContextStart, imageContextEnd)
                    val imageUrl = extractVisualImageUrl(imageContext)

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

        return items.distinctBy { normalizedTitle(it.title) }
    }

    private fun findVisualMatch(
        headline: Win98NewsItem,
        visual: List<Win98NewsItem>
    ): Win98NewsItem? {
        val target = normalizedTitle(headline.title)
        visual.firstOrNull { normalizedTitle(it.title) == target }?.let { return it }

        val targetTokens = titleTokens(headline.title)
        if (targetTokens.isEmpty()) return null

        return visual
            .map { item ->
                val candidate = titleTokens(item.title)
                val intersection = targetTokens.intersect(candidate).size.toFloat()
                val union = targetTokens.union(candidate).size.toFloat().coerceAtLeast(1f)
                item to (intersection / union)
            }
            .filter { (_, score) -> score >= 0.55f }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun titleTokens(value: String): Set<String> =
        normalizedTitle(value)
            .split(' ')
            .filter { it.length >= 3 }
            .take(14)
            .toSet()

    private fun extractVisualImageUrl(html: String): String? {
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
                normalizeImageUrl(raw)?.let { if (looksLikeUsableImage(it)) return it }
            }

            val srcset = attributeValue(tag, "srcset")
            if (!srcset.isNullOrBlank()) {
                srcset.split(',')
                    .asReversed()
                    .map { it.trim().substringBefore(' ') }
                    .forEach { raw ->
                        normalizeImageUrl(raw)?.let {
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
            normalizeImageUrl(raw)?.let { if (looksLikeUsableImage(it)) return it }
        }
        return null
    }

    private fun normalizeImageUrl(raw: String): String? {
        val value = decodeHtml(raw).trim()
        if (value.isBlank() || value.startsWith("data:", ignoreCase = true)) return null
        return when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("./") -> "https://news.google.com/" + value.removePrefix("./")
            value.startsWith("/") -> "https://news.google.com$value"
            else -> null
        }
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

    private fun loadRssHeadlines(limit: Int): List<Win98NewsItem> {
        val connection = openConnection(
            FEED_URL,
            3_500,
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
                                "description" -> if (inItem) {
                                    val description = parser.nextText()
                                    if (imageUrl.isNullOrBlank()) {
                                        imageUrl = extractVisualImageUrl(description)
                                    }
                                }
                                "media:content", "media:thumbnail", "enclosure" -> if (inItem) {
                                    val candidate = parser.getAttributeValue(null, "url")
                                    if (!candidate.isNullOrBlank() && looksLikeUsableImage(candidate)) {
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
                                items += Win98NewsItem(
                                    title = cleanTitle,
                                    source = resolvedSource.ifBlank { "Google News" },
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
