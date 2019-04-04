/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.server

import android.content.Context
import android.content.res.AssetManager
import org.nanohttpd.router.RouterNanoHTTPD
import org.readium.r2.readText
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.Container
import org.readium.r2.streamer.fetcher.Fetcher
import org.readium.r2.streamer.server.handler.*
import org.readium.r2.toFile
import java.io.File
import java.net.URL

class Server(port: Int) : AbstractServer(port)

abstract class AbstractServer(port: Int) : RouterNanoHTTPD("127.0.0.1", port) {
    companion object {
        const val SEARCH_HANDLE = "/search"
        const val MANIFEST_HANDLE = "/manifest"
        const val JSON_MANIFEST_HANDLE = "/manifest.json"
        const val MANIFEST_ITEM_HANDLE = "/(.*)"
        const val MEDIA_OVERLAY_HANDLE = "/media-overlay"
        const val CSS_HANDLE = "/styles/(.*)"
        const val JS_HANDLE = "/scripts/(.*)"
        const val FONT_HANDLE = "/fonts/(.*)"
    }

    private var containsMediaOverlay = false

    val resources = Ressources()
    val fonts = Fonts()

    fun addFont(name: String, assets: AssetManager, context: Context) {
        val d = context.getExternalFilesDir(null).path + "/fonts/"
        val f = File("$d/$name")
        if (f.exists())
            return
        File(d).mkdirs()
        assets.open("fonts/$name").toFile(f)
        fonts.add(name, f)
    }

    fun loadResources(assets: AssetManager, context: Context) {
        mapOf(
            "ltr-after.css" to "ReadiumCSS/ltr/ReadiumCSS-after.css",
            "ltr-before.css" to "ReadiumCSS/ltr/ReadiumCSS-before.css",
            "ltr-default.css" to "ReadiumCSS/ltr/ReadiumCSS-default.css",
            "rtl-after.css" to "ReadiumCSS/rtl/ReadiumCSS-after.css",
            "rtl-before.css" to "ReadiumCSS/rtl/ReadiumCSS-before.css",
            "rtl-default.css" to "ReadiumCSS/rtl/ReadiumCSS-default.css",
            "cjkv-after.css" to "ReadiumCSS/cjk-vertical/ReadiumCSS-after.css",
            "cjkv-before.css" to "ReadiumCSS/cjk-vertical/ReadiumCSS-before.css",
            "cjkv-default.css" to "ReadiumCSS/cjk-vertical/ReadiumCSS-default.css",
            "cjkh-after.css" to "ReadiumCSS/cjk-horizontal/ReadiumCSS-after.css",
            "cjkh-before.css" to "ReadiumCSS/cjk-horizontal/ReadiumCSS-before.css",
            "cjkh-default.css" to "ReadiumCSS/cjk-horizontal/ReadiumCSS-default.css",
            "touchHandling.js" to "ReadiumCSS/touchHandling.js",
            "utils.js" to "ReadiumCSS/utils.js"
        ).forEach { (name: String, fileName: String) ->
            resources.add(name, assets.readText(fileName))
        }

        addFont("OpenDyslexic-Regular.otf", assets, context)
    }

    fun addEpub(publication: Publication, container: Container, fileName: String, userPropertiesPath: String?) {
        val fetcher = Fetcher(publication, container, userPropertiesPath)

        addLinks(publication, fileName)

        publication.addSelfLink(fileName, URL("$BASE_URL:$myPort"))

        if (containsMediaOverlay) {
            addRoute(fileName + MEDIA_OVERLAY_HANDLE, MediaOverlayHandler::class.java, publication)
        }
        addRoute(fileName + JSON_MANIFEST_HANDLE, ManifestHandler::class.java, publication)
        addRoute(fileName + MANIFEST_HANDLE, ManifestHandler::class.java, publication)
        addRoute(fileName + SEARCH_HANDLE, SearchQueryHandler::class.java, fetcher)
        addRoute(fileName + MANIFEST_ITEM_HANDLE, ResourceHandler::class.java, fetcher)
        addRoute(JS_HANDLE, JSHandler::class.java, resources)
        addRoute(CSS_HANDLE, CSSHandler::class.java, resources)
        addRoute(FONT_HANDLE, FontHandler::class.java, fonts)
    }

    private fun addLinks(publication: Publication, filePath: String) {
        containsMediaOverlay = false
        for (link in publication.otherLinks) {
            if (link.rel.contains("media-overlay")) {
                containsMediaOverlay = true
                link.href = link.href?.replace("port", "127.0.0.1:$listeningPort$filePath")
            }
        }
    }
}

