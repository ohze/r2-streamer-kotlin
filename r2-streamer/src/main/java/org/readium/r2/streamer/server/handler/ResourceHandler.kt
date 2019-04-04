/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.server.handler

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.NanoHTTPD.MIME_PLAINTEXT
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newChunkedResponse
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.streamer.fetcher.Fetcher
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

class ResourceHandler : RouterNanoHTTPD.DefaultHandler() {
    override fun getMimeType(): String? = null

    override fun getText() = ResponseStatus.FAILURE_RESPONSE

    override fun getStatus() = Status.OK

    override fun get(uriResource: RouterNanoHTTPD.UriResource, urlParams: Map<String, String>?,
                     session: IHTTPSession?): Response? {
        try {
            Timber.v("Method: ${session!!.method}, Uri: ${session.uri}")
            val fetcher = uriResource.initParameter(Fetcher::class.java)

            val filePath = getHref(session.uri)
            val link = fetcher.box.publication.linkWithHref(filePath)
                ?: return newFixedLengthResponse(Status.NOT_FOUND, "text/html", byteArrayOf())
            val mimeType = link.typeLink!!

            // If the content is of type html return the response this is done to
            // skip the check for following font deobfuscation check
            if (mimeType == "application/xhtml+xml") {
                return serveResponse(session, fetcher.dataStream(filePath, link), mimeType)
            }

            // ********************
            //  FONT DEOBFUSCATION
            // ********************

            return serveResponse(session, fetcher.dataStream(filePath, link), mimeType)
        } catch (e: Exception) {
            Timber.e(e)
            return newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType, ResponseStatus.FAILURE_RESPONSE)
        }
    }

    data class RangeQuery(val start: Long, var end: Long) {
        fun checkEnd(streamLength: Long) {
            if (end < 0) end = streamLength - 1
        }
        fun contentLen() = max(0L, end - start + 1)
        fun contentRange(streamLength: Long) = "bytes $start-$end/$streamLength"
    }

    /** Support skipping
     * return [start, end] as byte index in inputStream
     * if session.headers["range"] in form: bytes=start-end */
    private fun rangeQuery(session: IHTTPSession): RangeQuery? {
        val r: String = session.headers["range"] ?: return null
        if (!r.startsWith("bytes=")) return null
        val l = "bytes=".length
        val minusIdx = r.indexOf('-', l)
        if (minusIdx < 0) return null
        return try {
            val start = r.substring(l, l + minusIdx).toLong()
            val end = r.substring(l + minusIdx + 1).toLong()
            RangeQuery(start, end)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun serveResponse(session: IHTTPSession, inputStream: InputStream, mimeType: String): Response {
        try {
            // Calculate etag
            val etag = Integer.toHexString(inputStream.hashCode())
            // Support skipping:
            val r = rangeQuery(session)
            // Change return code and add Content-Range header when skipping is requested
            val streamLength = inputStream.available().toLong()
            return when {
                r != null && r.start >= 0 ->
                    if (r.start >= streamLength) {
                        createResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                            addHeader("Content-Range", "bytes 0-0/$streamLength")
                            addHeader("ETag", etag)
                        }
                    } else {
                        r.checkEnd(streamLength)
                        inputStream.skip(r.start)
                        createResponse(Status.PARTIAL_CONTENT, mimeType, inputStream).apply {
                            addHeader("Content-Length", r.contentLen().toString())
                            addHeader("Content-Range", r.contentRange(streamLength))
                            addHeader("ETag", etag)
                        }
                    }
                else ->
                    if (etag == session.headers["if-none-match"])
                        createResponse(Status.NOT_MODIFIED, mimeType, "")
                    else {
                        createResponse(Status.OK, mimeType, inputStream).apply {
                            addHeader("Content-Length", streamLength.toString())
                            addHeader("ETag", etag)
                        }
                    }
            }
        } catch (ioe: IOException) {
            return getResponse("Forbidden: Reading file failed")
        } catch (ioe: NullPointerException) {
            return getResponse("Forbidden: Reading file failed")
        }
//        return getResponse("Error 404: File not found")
    }

    private fun createResponse(status: Status, mimeType: String, message: InputStream) =
        newChunkedResponse(status, mimeType, message).apply {
            addHeader("Accept-Ranges", "bytes")
        }

    private fun createResponse(status: Status, mimeType: String, message: String) =
        newFixedLengthResponse(status, mimeType, message).apply {
            addHeader("Accept-Ranges", "bytes")
        }

    private fun getResponse(message: String) = createResponse(Status.OK, "text/plain", message)

    private fun getHref(path: String): String {
        val offset = path.indexOf("/", 0)
        val startIndex = path.indexOf("/", offset + 1)
        return path.substring(startIndex + 1)
    }
}
