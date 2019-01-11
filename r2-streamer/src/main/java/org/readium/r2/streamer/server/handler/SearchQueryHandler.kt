package org.readium.r2.streamer.server.handler

import android.annotation.SuppressLint
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

    private val searchLocators: MutableList<Locator> = mutableListOf()
    private lateinit var webView: WebView

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
        Log.i(LOG_TAG, "-> ${session.method} ${session.uri}")

        return try {
            val fetcher = uriResource.initParameter(Fetcher::class.java)
            val spineIndex = session.parameters["spineIndex"]?.get(0)?.toInt() ?: -1
            val link = fetcher.publication.readingOrder[spineIndex]
            val searchQueryEncoded = session.parameters["query"]?.get(0)
            val searchQuery = URLDecoder.decode(searchQueryEncoded, "UTF-8")

            //val responseSearchLocators = rangyFindSolution(link, searchQuery, fetcher)
            val responseSearchLocators = windowFindSolution(link, searchQuery, fetcher)

            val objectMapper = ObjectMapper()
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
            val searchLocatorsJson = objectMapper.writeValueAsString(responseSearchLocators)
            Response.newFixedLengthResponse(Status.OK, mimeType, searchLocatorsJson)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "-> get -> ", e)
            Response.newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType,
                    ResponseStatus.FAILURE_RESPONSE)
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun rangyFindSolution(link: Link, searchQuery: String, fetcher: Fetcher):
            MutableList<Locator> {
        Log.d(LOG_TAG, "-> rangyFindSolution -> ${link.href}")

        if (link.typeLink != "application/xhtml+xml")
            return mutableListOf()

        val href = link.href!!.substring(1)
        val fileData = String(fetcher.container.data(href))

        val handler = Handler(Looper.getMainLooper())
        handler.postAtFrontOfQueue {
            runWebviewForRangyFind(link, searchQuery, fileData)
        }

        synchronized(this) {
            (this as java.lang.Object).wait(60000)
        }

        return searchLocators
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runWebviewForRangyFind(link: Link, searchQuery: String, fileData: String) {
        Log.v(LOG_TAG, "-> runWebviewForRangyFind -> ${link.href}")

        val context = ClientAppContext.get()
        webView = WebView(context)
        webView.settings.javaScriptEnabled = true

        val scriptTagTemplate = "<script type=\"text/javascript\" src=\"%s\"></script>\n"
        val assetsPath = "file:///android_asset/org/readium/r2/streamer/"
        var jsInjection = String.format(scriptTagTemplate, assetsPath + "js/search-bridge.js")
        jsInjection += String.format(scriptTagTemplate, assetsPath + "js/libs/rangy/rangy-core.js")
        jsInjection += String.format(scriptTagTemplate, assetsPath + "js/libs/rangy/rangy-textrange.js")
        jsInjection += String.format(scriptTagTemplate, assetsPath + "js/libs/cfi/develop/readium-cfi.umd.js")

        val modifiedFileData = fileData.replace("</head>", "$jsInjection</head>")

        webView.webViewClient = object : WebViewClient() {

            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.v(LOG_TAG, "-> onPageFinished -> ${link.href}")

                var searchMethod = "javascript:getLocatorsUsingRangyFind(\"%s\")"
                searchMethod = String.format(searchMethod, searchQuery)

                webView.evaluateJavascript(searchMethod) { value: String? ->
                    Log.v(LOG_TAG, "-> getLocatorsUsingRangyFind returned -> ${link.href}")

                    addLocators(value, link)
                    synchronized(this@SearchQueryHandler) {
                        (this@SearchQueryHandler as java.lang.Object).notify()
                    }
                }
            }
        }

        webView.loadDataWithBaseURL("", modifiedFileData, link.typeLink, "UTF-8", null)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun windowFindSolution(link: Link, searchQuery: String, fetcher: Fetcher):
            MutableList<Locator> {
        Log.d(LOG_TAG, "-> windowFindSolution -> ${link.href}")

        if (link.typeLink != "application/xhtml+xml")
            return mutableListOf()

        val href = link.href!!.substring(1)
        val fileData = String(fetcher.container.data(href))

        val handler = Handler(Looper.getMainLooper())
        handler.postAtFrontOfQueue {
            runWebviewForWindowFind(link, searchQuery, fileData)
        }

        synchronized(this) {
            (this as java.lang.Object).wait(60000)
        }

        return searchLocators
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runWebviewForWindowFind(link: Link, searchQuery: String, fileData: String) {
        Log.v(LOG_TAG, "-> runWebviewForWindowFind -> ${link.href}")

        val context = ClientAppContext.get()
        webView = WebView(context)
        webView.settings.javaScriptEnabled = true

        val scriptTagTemplate = "<script type=\"text/javascript\" src=\"%s\"></script>\n"
        val assetsPath = "file:///android_asset/org/readium/r2/streamer/"
        var jsInjection = String.format(scriptTagTemplate, assetsPath + "js/search-bridge.js")
        jsInjection += String.format(scriptTagTemplate, assetsPath + "js/libs/cfi/develop/readium-cfi.umd.js")

        val modifiedFileData = fileData.replace("</head>", "$jsInjection</head>")

        webView.webViewClient = object : WebViewClient() {

            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.v(LOG_TAG, "-> onPageFinished -> ${link.href}")

                var searchMethod = "javascript:getLocatorsUsingWindowFind(\"%s\")"
                searchMethod = String.format(searchMethod, searchQuery)

                webView.evaluateJavascript(searchMethod) { value: String? ->
                    Log.v(LOG_TAG, "-> getLocatorsUsingWindowFind returned -> ${link.href}")

                    addLocators(value, link)
                    synchronized(this@SearchQueryHandler) {
                        (this@SearchQueryHandler as java.lang.Object).notify()
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