/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.fetcher

import org.readium.r2.shared.Link
import org.readium.r2.shared.Publication
import org.readium.r2.streamer.container.Container
import org.readium.r2.streamer.parser.PubBox
import java.io.InputStream

class Fetcher(val box: PubBox,
              private val userPropertiesPath: String?,
              private val autoInjectHtml: Boolean) {

    @Deprecated("use primary constructor")
    constructor(publication: Publication, container: Container, userPropertiesPath: String?)
        : this(PubBox(publication, container), userPropertiesPath, true)

    constructor(box: PubBox): this(box, null, false)

    private val rootFileDirectory: String
    private val contentFilters: ContentFilters

    init {
        val rootFilePath = box.publication.internalData["rootfile"]
                ?: throw Exception("Missing root file")
        rootFileDirectory = if (rootFilePath.contains('/')) {
            rootFilePath.replaceAfterLast("/", "", rootFilePath).dropLast(1)
        } else {
            ""
        }
        contentFilters = getContentFilters(box.container.rootFile.mimetype)
    }

    fun dataStream(path: String, link: Link): InputStream {
        return box.container.dataInputStream(path).let {
            contentFilters.apply(it, box, link)
        }
    }

    private fun getContentFilters(mimeType: String?): ContentFilters {
        return when (mimeType) {
            "application/epub+zip", "application/oebps-package+xml" ->
                if (autoInjectHtml) ContentFiltersEpub(userPropertiesPath)
                else DecodingContentFilters()
            "application/vnd.comicbook+zip", "application/x-cbr" -> ContentFiltersCbz()
            else -> throw Exception("Missing container or MIMEtype")
        }
    }
}
