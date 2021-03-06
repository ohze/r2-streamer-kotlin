/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.server.handler

import android.webkit.MimeTypeMap
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Response.newChunkedResponse
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.streamer.server.Fonts
import timber.log.Timber

class FontHandler : RouterNanoHTTPD.DefaultHandler() {
    override fun getMimeType(): String? = null

    override fun getText() = ResponseStatus.FAILURE_RESPONSE

    override fun getStatus() = Status.OK

    override fun get(uriResource: RouterNanoHTTPD.UriResource?, urlParams: Map<String, String>?, session: IHTTPSession): Response {
        Timber.v("%s: %s", session.method, session.uri)
        return try {
            val uri = session.uri.substringAfterLast('/')
            val fonts = uriResource!!.initParameter(Fonts::class.java)
            newChunkedResponse(Status.OK, getMimeType(uri), fonts.get(uri).inputStream()).apply {
                addHeader("Accept-Ranges", "bytes")
            }
        } catch (e: Exception) {
            Timber.e(e)
            newFixedLengthResponse(Status.INTERNAL_ERROR, mimeType, ResponseStatus.FAILURE_RESPONSE)
        }
    }

    private fun getMimeType(url: String) = when(MimeTypeMap.getFileExtensionFromUrl(url)) {
        "woff" -> "application/font-woff"
        "otf" -> "application/vnd.ms-opentype"
        else -> "application/vnd.ms-opentype"
    }
}
