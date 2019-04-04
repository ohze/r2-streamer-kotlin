/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.fetcher

import org.readium.r2.shared.drm.Drm
import org.readium.r2.shared.Link
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.Inflater

class DrmDecoder {
    fun decoding(input: InputStream, resourceLink: Link, drm: Drm?): InputStream {
        if (drm == null) return input

        val scheme = resourceLink.properties.encryption?.scheme ?: return input

        if (scheme != drm.scheme) return input

        var data = decipher(input, drm) ?: return input

        if (resourceLink.properties.encryption?.compression == "deflate") {
            val padding = data[data.size - 1].toInt()
            data = Inflater(true).inflateAll(data, 0, data.size - padding)
        }
        return ByteArrayInputStream(data)
    }

    private fun Inflater.inflateAll(data: ByteArray, off: Int, len: Int): ByteArray {
        setInput(data, off, len)
        val buf = ByteArray(1024)
        val output = ByteArrayOutputStream(len)
        while (!finished()) {
            val count = inflate(buf)
            output.write(buf, 0, count)
        }
        // Closing a ByteArrayOutputStream has no effect
        return output.toByteArray()
    }

    private fun decipher(input: InputStream, drm: Drm): ByteArray? {
        val drmLicense = drm.license ?: return null
        val buffer = input.readBytes()
        return drmLicense.decipher(buffer)
    }
}
