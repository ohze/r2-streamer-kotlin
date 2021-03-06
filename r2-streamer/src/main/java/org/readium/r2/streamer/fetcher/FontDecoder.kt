/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.fetcher

import org.readium.r2.toHexBytes
import org.readium.r2.sha1
import org.readium.r2.shared.Link
import org.readium.r2.shared.Publication
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.experimental.xor

class FontDecoder {
    private val adobe = 1024
    private val idpf = 1040

    private var decodableAlgorithms = mapOf(
            "fontIdpf" to "http://www.idpf.org/2008/embedding",
            "fontAdobe" to "http://ns.adobe.com/pdf/enc#RC")
    private var decoders = mapOf(
            "http://www.idpf.org/2008/embedding" to idpf,
            "http://ns.adobe.com/pdf/enc#RC" to adobe
    )

    fun decoding(input: InputStream, pubId: String, link: Link): InputStream {
        val encryption = link.properties.encryption ?: return input
        val algorithm = encryption.algorithm ?: return input
        val type = decoders[link.properties.encryption?.algorithm] ?: return input
        if (!decodableAlgorithms.values.contains(algorithm)) {
            Timber.e("$${link.href} is encrypted, but can't handle it")
            return input
        }
        return decodingFont(input, pubId, type)
    }

    fun decoding(input: InputStream, publication: Publication, path: String): InputStream {
        val link = publication.linkWithHref(path) ?: return input
        return decoding(input, publication.metadata.identifier, link)
    }

    private fun decodingFont(input: InputStream, pubId: String, length: Int): ByteArrayInputStream {
        val publicationKey: ByteArray = when (length) {
            adobe -> getHashKeyAdobe(pubId)
            else -> pubId.sha1().toHexBytes()
        }
        return ByteArrayInputStream(deobfuscate(input, publicationKey, length))
    }

    private fun deobfuscate(input: InputStream, publicationKey: ByteArray, obfuscationLength: Int): ByteArray {
        val buffer = input.readBytes()
        val count = if (buffer.size > obfuscationLength) obfuscationLength else buffer.size
        for (i in 0 until count)
            buffer[i] = buffer[i].xor(publicationKey[i % publicationKey.size])
        return buffer
    }

    private fun getHashKeyAdobe(pubId: String) =
            pubId.replace("urn:uuid:", "")
                    .replace("-", "")
                    .toHexBytes()

}
