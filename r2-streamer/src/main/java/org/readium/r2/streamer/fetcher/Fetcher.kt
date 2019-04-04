/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.fetcher

import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.Container
import java.io.InputStream

class Fetcher(val publication: Publication, val container: Container, private val userPropertiesPath: String?) {
    private val rootFileDirectory: String
    private val contentFilters: ContentFilters

    init {
        val rootFilePath = publication.internalData["rootfile"]
                ?: throw Exception("Missing root file")
        rootFileDirectory = if (rootFilePath.contains('/')) {
            rootFilePath.replaceAfterLast("/", "", rootFilePath).dropLast(1)
        } else {
            ""
        }
        contentFilters = getContentFilters(container.rootFile.mimetype)
    }

    fun data(path: String): ByteArray {
        return container.data(path).let {
            contentFilters.apply(it, publication, container, path)
        }
    }

    fun dataStream(path: String): InputStream {
        return container.dataInputStream(path).let {
            contentFilters.apply(it, publication, container, path)
        }
    }

    fun dataLength(path: String): Long {
        val relativePath = rootFileDirectory.plus(path)

        publication.resource(path) ?: throw Exception("Missing file")
        return container.dataLength(relativePath)
    }

    private fun getContentFilters(mimeType: String?): ContentFilters {
        return when (mimeType) {
            "application/epub+zip", "application/oebps-package+xml" -> ContentFiltersEpub(userPropertiesPath)
            "application/vnd.comicbook+zip", "application/x-cbr" -> ContentFiltersCbz()
            else -> throw Exception("Missing container or MIMEtype")
        }
    }
}