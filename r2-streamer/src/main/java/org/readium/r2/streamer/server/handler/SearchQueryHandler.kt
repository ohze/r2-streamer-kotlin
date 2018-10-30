package org.readium.r2.streamer.server.handler

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.IStatus
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.shared.Link
import org.readium.r2.streamer.ClientAppContext
import org.readium.r2.streamer.fetcher.Fetcher
import java.net.URLDecoder

class SearchQueryHandler : RouterNanoHTTPD.DefaultHandler() {

    val LOG_TAG = SearchQueryHandler::class.java.simpleName
    lateinit var lastLink: Link

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
        Log.d(LOG_TAG, "-> $method $uri")
        var response: Response

        try {
            val fetcher = uriResource.initParameter(Fetcher::class.java)

            val queryParameter = session.queryParameterString
            val startIndex = queryParameter.indexOf("=")
            val searchQueryEncoded = queryParameter.substring(startIndex + 1)
            val searchQuery = URLDecoder.decode(searchQueryEncoded, "UTF-8")

            solution1(fetcher)

            response = Response.newFixedLengthResponse(Status.OK, mimeType, ResponseStatus.SUCCESS_RESPONSE)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "-> get -> ", e)
            response = Response.newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType,
                    ResponseStatus.FAILURE_RESPONSE)
        }

        return response
    }

    private fun solution1(fetcher: Fetcher) {

        for (link in fetcher.publication.spine) {
            Log.d(LOG_TAG, "-> solution1 -> ${link.href}")

            val href = link.href!!.substring(1)
            val fileData = String(fetcher.container.data(href))

            val handler = Handler(Looper.getMainLooper())
            handler.post {
                runWebview(link, fileData)
            }
        }

        lastLink = fetcher.publication.spine.last()

        synchronized(this) {
            (this as java.lang.Object).wait(20000)
        }
    }

    private fun runWebview(link: Link, fileData: String) {
        Log.d(LOG_TAG, "-> runWebview -> ${link.href}")

        val context = ClientAppContext.get()
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(LOG_TAG, "-> onPageFinished -> ${link.href}")

                val jsMethod = "javascript: (function(){ return \"-> Returned from JS\"; })()"
                webView.evaluateJavascript(jsMethod) { value: String? ->
                    Log.d(LOG_TAG, value)

                    if (link == lastLink) {
                        Log.d(LOG_TAG, "-> Done")
                        synchronized(this@SearchQueryHandler) {
                            (this@SearchQueryHandler as java.lang.Object).notify()
                        }
                    }
                }
            }
        }

        webView.loadData(fileData, link.typeLink, "UTF-8")
    }
}