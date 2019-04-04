package org.readium.r2.streamer.server.handler

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.json.JSONArray
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.shared.LocatorText
import org.readium.r2.streamer.ClientAppContext
import org.readium.r2.streamer.fetcher.Fetcher
import timber.log.Timber
import java.net.URLDecoder
import java.util.*

class SearchQueryHandler : RouterNanoHTTPD.DefaultHandler() {
    private val searchLocators = mutableListOf<Locator>()
    private lateinit var webView: WebView

    override fun getStatus() = Status.OK

    override fun getMimeType() = "application/json"

    override fun getText() = ResponseStatus.FAILURE_RESPONSE

    override fun get(uriResource: RouterNanoHTTPD.UriResource,
                     urlParams: MutableMap<String, String>?, session: IHTTPSession): Response {
        Timber.v("%s: %s", session.method, session.uri)

        return try {
            val fetcher = uriResource.initParameter(Fetcher::class.java)
            val spineIndex = session.parameters["spineIndex"]?.get(0)?.toInt() ?: -1
            val link = fetcher.publication.readingOrder[spineIndex]
            val searchQueryEncoded = session.parameters["query"]?.get(0)
            val searchQuery = URLDecoder.decode(searchQueryEncoded, "UTF-8")

            //val responseSearchLocators = rangyFindSolution(link, searchQuery, fetcher)
            val responseSearchLocators = find(link, searchQuery, fetcher, false)

            val objectMapper = ObjectMapper()
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            val searchLocatorsJson = objectMapper.writeValueAsString(responseSearchLocators)
            Response.newFixedLengthResponse(Status.OK, mimeType, searchLocatorsJson)
        } catch (e: Exception) {
            Timber.e(e, "-> get -> ")
            Response.newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType,
                    ResponseStatus.FAILURE_RESPONSE)
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun find(link: Link, searchQuery: String, fetcher: Fetcher, useRangy: Boolean): MutableList<Locator> {
        Timber.d("-> find using rangy: %b -> %s", useRangy, link.href)

        if (link.typeLink != "application/xhtml+xml")
            return mutableListOf()

        val href = link.href!!.substring(1)
        val fileData = String(fetcher.container.data(href))

        val handler = Handler(Looper.getMainLooper())
        handler.postAtFrontOfQueue {
            runWebViewToFind(link, searchQuery, fileData, useRangy)
        }

        synchronized(this) {
            (this as java.lang.Object).wait(60000)
        }

        return searchLocators
    }

    private fun injectJsToFind(html: String, useRangy: Boolean): String {
        val jsFiles = if (useRangy) {
            listOf("search-bridge",
                "libs/rangy/rangy-core",
                "libs/rangy/rangy-textrange",
                "libs/cfi/develop/readium-cfi.umd")
        } else {
            listOf("search-bridge",
                "libs/cfi/develop/readium-cfi.umd")
        }
        val jsInjection = jsFiles.joinToString(separator = "\n") {
            """<script type="text/javascript"
                  |     src="file:///android_asset/org/readium/r2/streamer/js/$it.js">
                  |</script>\n""".trimMargin()
        }
        return html.replaceFirst("</head>", "$jsInjection</head>")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runWebViewToFind(link: Link, searchQuery: String, fileData: String, useRangy: Boolean) {
        webView = WebView(ClientAppContext.get()).apply {
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                override fun onPageFinished(view: WebView, url: String?) {
                    Timber.v("-> onPageFinished -> ${link.href}")
                    val searchMethod =
                        if (useRangy) "getLocatorsUsingRangyFind"
                        else "getLocatorsUsingWindowFind"
                    webView.evaluateJavascript("""javascript:$searchMethod("$searchQuery")""") { value: String? ->
                        Timber.v("-> getLocators useRangy: %b returned -> %s", useRangy, link.href)

                        addLocators(value, link)
                        synchronized(this@SearchQueryHandler) {
                            (this@SearchQueryHandler as java.lang.Object).notify()
                        }
                    }
                }
            }
        }

        webView.loadDataWithBaseURL("",
            injectJsToFind(fileData, useRangy), link.typeLink, "UTF-8", null)
    }

    private fun addLocators(locatorsJsonString: String?, link: Link) {
        //Log.d(LOG_TAG, "-> addLocators")

        try {
            val locatorsJson = JSONArray(locatorsJsonString)

            for (i in 0 until locatorsJson.length()) {

                val locatorJson = locatorsJson.getJSONObject(i)
                val locationsJson = locatorJson.getJSONObject("locations")
                val locations = Locations()
                locations.cfi = locationsJson.getString("cfi")

                val textJson = locatorJson.getJSONObject("text")
                val text = LocatorText()
                text.before = textJson.getString("before")
                text.hightlight = textJson.getString("highlight")
                text.after = textJson.getString("after")

                val title = locatorJson.optString("title")

                val locator = Locator(link.href!!, Date().time, title, locations, text)
                searchLocators.add(locator)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
