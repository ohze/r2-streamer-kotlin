package org.readium.r2.streamer.server.handler

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import org.json.JSONArray
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.IStatus
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.shared.LocatorText
import org.readium.r2.streamer.ClientAppContext
import org.readium.r2.streamer.fetcher.Fetcher
import java.net.URLDecoder
import java.util.*

class SearchQueryHandler : RouterNanoHTTPD.DefaultHandler() {

    companion object {
        val LOG_TAG: String = SearchQueryHandler::class.java.simpleName
    }

    private lateinit var searchQuery: String
    private var spineCount: Int = 0
    private var spineTracker: Int = 0
    private val searchLocators: MutableList<Locator> = mutableListOf()
    private val webviewList: MutableList<WebView> = mutableListOf()

    override fun getStatus(): IStatus {
        return Status.OK
    }

    override fun getMimeType(): String {
        return "application/json"
    }

    override fun getText(): String {
        return ResponseStatus.FAILURE_RESPONSE
    }

    override fun get(uriResource: RouterNanoHTTPD.UriResource,
                     urlParams: MutableMap<String, String>?, session: IHTTPSession): Response {

        val method = session.method
        val uri = session.uri
        Log.i(LOG_TAG, "-> $method $uri")
        var response: Response

        try {
            val fetcher = uriResource.initParameter(Fetcher::class.java)

            val queryParameter = session.queryParameterString
            val startIndex = queryParameter.indexOf("=")
            val searchQueryEncoded = queryParameter.substring(startIndex + 1)
            searchQuery = URLDecoder.decode(searchQueryEncoded, "UTF-8")

            rangySolution(fetcher)

            val objectMapper = ObjectMapper()
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            val searchLocatorsJson = objectMapper.writeValueAsString(searchLocators)
            response = Response.newFixedLengthResponse(Status.OK, mimeType, searchLocatorsJson)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "-> get -> ", e)
            response = Response.newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType,
                    ResponseStatus.FAILURE_RESPONSE)
        }

        return response
    }

    private fun rangySolution(fetcher: Fetcher) {

        spineCount = fetcher.publication.spine.size

        for (link in fetcher.publication.spine) {
            Log.d(LOG_TAG, "-> rangySolution -> ${link.href}")

            if (link.typeLink != "application/xhtml+xml") {
                ++spineTracker
                continue
            }

            val href = link.href!!.substring(1)
            val fileData = String(fetcher.container.data(href))

            val handler = Handler(Looper.getMainLooper())
            handler.post {
                runWebview(link, fileData)
            }
        }

        synchronized(this) {
            (this as java.lang.Object).wait(60000)
        }

        Log.d(LOG_TAG, "-> rangySolution -> $spineTracker / $spineCount done")
    }

    private fun runWebview(link: Link, fileData: String) {
        Log.v(LOG_TAG, "-> runWebview -> ${link.href}")

        val context = ClientAppContext.get()
        val webView = WebView(context)
        webviewList.add(webView)
        webView.settings.javaScriptEnabled = true

        val scriptTagTemplate = "<script type=\"text/javascript\" src=\"%s\"></script>\n"
        var jsInjection = String.format(scriptTagTemplate, "file:///android_asset/org/readium/r2/streamer/Bridge.js")
        jsInjection += String.format(scriptTagTemplate, "file:///android_asset/org/readium/r2/streamer/rangy-core.js")
        jsInjection += String.format(scriptTagTemplate, "file:///android_asset/org/readium/r2/streamer/rangy-textrange.js")
        jsInjection += String.format(scriptTagTemplate, "file:///android_asset/org/readium/r2/streamer/readium-cfi.umd.js")

        val modifiedFileData = fileData.replace("</head>", "$jsInjection</head>")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.v(LOG_TAG, "-> onPageFinished -> ${link.href}")

                var searchMethod = "javascript:getLocatorsUsingRangySearch(\"%s\")"
                searchMethod = String.format(searchMethod, searchQuery)

                webView.evaluateJavascript(searchMethod) { value: String? ->
                    Log.v(LOG_TAG, "-> getLocatorsUsingRangySearch returned -> ${link.href}")

                    addLocators(value, link)
                    ++spineTracker

                    if (spineCount == spineTracker) {
                        Log.d(LOG_TAG, "-> Done")
                        synchronized(this@SearchQueryHandler) {
                            (this@SearchQueryHandler as java.lang.Object).notify()
                        }
                    }
                }
            }
        }

        webView.loadDataWithBaseURL("", modifiedFileData, link.typeLink, "UTF-8", null)
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
            Log.e(LOG_TAG, "->", e)
        }
    }
}