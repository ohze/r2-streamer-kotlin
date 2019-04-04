/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.container

import org.readium.r2.shared.RootFile
import org.readium.r2.shared.drm.Drm
import org.readium.r2.streamer.parser.mimetypeCBZ
import java.io.File
import java.util.zip.ZipFile


class ContainerCbz(path: String) : CbzContainer, ZipArchiveContainer {
    override val rootFile = RootFile(path, mimetypeCBZ)
    override val zipFile = ZipFile(path)
    override var drm: Drm? = null
    override val successCreated: Boolean = File(path).exists()

    override fun getFilesList(): List<String> {
        val filesList = mutableListOf<String>()
        zipFile.let {
            val listEntries = it.entries()
            listEntries.toList().forEach { filesList.add(it.toString()) }
        }
        return filesList
    }
}