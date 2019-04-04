/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.container

import java.io.File
import java.io.FileInputStream
import java.net.URI

interface DirectoryContainer : Container {
    override fun data(relativePath: String): ByteArray {
        val decodedFilePath = rootFile.rootPath + "/" + getDecodedRelativePath(relativePath)
        val file = File(decodedFilePath)
        if (!file.exists())
            throw Exception("Missing File")
        return  file.readBytes()
    }

    override fun dataLength(relativePath: String) =
            File(rootFile.toString() + "/" + getDecodedRelativePath(relativePath)).length()

    override fun dataInputStream(relativePath: String) =
            FileInputStream(File(rootFile.toString() + "/" + getDecodedRelativePath(relativePath)))

    fun getDecodedRelativePath(relativePath: String): String {
        return URI(relativePath).path
    }
}

