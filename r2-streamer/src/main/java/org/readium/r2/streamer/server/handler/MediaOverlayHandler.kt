/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.server.handler

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.shared.Link
import org.readium.r2.shared.MediaOverlays
import org.readium.r2.shared.Publication

class MediaOverlayHandler : RouterNanoHTTPD.DefaultHandler() {

    override fun getText() = ResponseStatus.FAILURE_RESPONSE

    override fun getMimeType() = "application/webpub+json"

    override fun getStatus() = Status.OK

    override fun get(uriResource: RouterNanoHTTPD.UriResource, urlParams: Map<String, String>?, session: IHTTPSession): Response {
        val searchQueryPath = session.parameters["resource"]?.get(0)
            ?: return failed()

        val spines = uriResource.initParameter(Publication::class.java).resources
        val objectMapper = ObjectMapper()
        return try {
            val json = objectMapper.writeValueAsString(getMediaOverlay(spines, searchQueryPath))
            newFixedLengthResponse(status, mimeType, json)
        } catch (e: JsonProcessingException) {
            failed()
        }
    }

    private fun failed() = newFixedLengthResponse(status, mimeType, ResponseStatus.FAILURE_RESPONSE)

    private fun getMediaOverlay(spines: List<Link>, searchQueryPath: String): MediaOverlays? {
        for (link in spines) {
            if (link.href!!.contains(searchQueryPath)) {
                return link.mediaOverlays
            }
        }
        return MediaOverlays()
    }
}
