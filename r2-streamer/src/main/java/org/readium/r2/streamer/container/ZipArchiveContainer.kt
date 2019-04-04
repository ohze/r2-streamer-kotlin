/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.container

import java.io.InputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Template for an archive container ( E.g.: EPub, CBZ, .. )
 *
 * Contains the zipFile and methods to gather data, size of
 * the content or an inputStream of the archive
 *
 */
interface ZipArchiveContainer : Container {
    val zipFile: ZipFile

    override fun data(relativePath: String): ByteArray {
        val zipEntry = getEntry(relativePath)// ?: return ByteArray(0)
        return zipFile.getInputStream(zipEntry).use {
            it.readBytes()
        }
    }

    override fun dataLength(relativePath: String): Long {
        return zipFile.size().toLong()
    }

    override fun dataInputStream(relativePath: String): InputStream {
        return zipFile.getInputStream(getEntry(relativePath))
    }

    fun getEntry(relativePath: String): ZipEntry? {
        val path: String = try {
            URI(relativePath).path
        } catch (e: Exception) {
            relativePath
        }

        var zipEntry = zipFile.getEntry(path)
        if (zipEntry != null)
            return zipEntry

        val zipEntries = zipFile.entries()
        while (zipEntries.hasMoreElements()) {
            zipEntry = zipEntries.nextElement()
            if (path.equals(zipEntry.name, true))
                return zipEntry
        }

        return null
    }
}

