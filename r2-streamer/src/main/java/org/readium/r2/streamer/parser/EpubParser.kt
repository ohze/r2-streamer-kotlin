/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.parser

import org.readium.r2.shared.ContentLayoutStyle
import org.readium.r2.shared.drm.Drm
import org.readium.r2.shared.Encryption
import org.readium.r2.shared.LangType
import org.readium.r2.shared.Publication
import org.readium.r2.shared.parser.xml.XmlParser
import org.readium.r2.streamer.container.Container
import org.readium.r2.streamer.container.ContainerEpub
import org.readium.r2.streamer.container.ContainerEpubDirectory
import org.readium.r2.streamer.container.EpubContainer
import org.readium.r2.streamer.fetcher.forceScrollPreset
import org.readium.r2.streamer.fetcher.userSettingsUIPreset
import org.readium.r2.streamer.parser.epub.EncryptionParser
import org.readium.r2.streamer.parser.epub.NCXParser
import org.readium.r2.streamer.parser.epub.NavigationDocumentParser
import org.readium.r2.streamer.parser.epub.OPFParser
import timber.log.Timber
import java.io.File

// Some constants useful to parse an Epub document
const val defaultEpubVersion = 1.2
const val containerDotXmlPath = "META-INF/container.xml"
const val encryptionDotXmlPath = "META-INF/encryption.xml"
const val lcplFilePath = "META-INF/license.lcpl"
const val mimetype = "application/epub+zip"
const val mimetypeOEBPS = "application/oebps-package+xml"
const val mediaOverlayURL = "media-overlay?resource="

class EpubParser : PublicationParser {
    private fun generateContainerFrom(path: String): EpubContainer {
        val isDirectory = File(path).isDirectory
        val container: EpubContainer?

        if (!File(path).exists())
            throw Exception("Missing File")
        container = when (isDirectory) {
            true -> ContainerEpubDirectory(path)
            false -> ContainerEpub(path)
        }
        if (!container.successCreated)
            throw Exception("Missing File")
        return container
    }

    fun parseEncryption(container: Container, publication: Publication, drm: Drm?): Pair<Container, Publication> {
        container.drm = drm
        fillEncryptionProfile(publication, drm)

        return Pair(container, publication)
    }

    override fun parse(fileAtPath: String, title: String): PubBox? {
        val container = try {
            generateContainerFrom(fileAtPath)
        } catch (e: Exception) {
            Timber.e(e,"Could not generate container")
            return null
        }
        val data = try {
            container.data(containerDotXmlPath)
        } catch (e: Exception) {
            Timber.e(e, "Missing File : META-INF/container.xml")
            return null
        }

        container.rootFile.mimetype = mimetype
        container.rootFile.rootFilePath = getRootFilePath(data)

        val xmlParser = XmlParser()

        val documentData = try {
            container.data(container.rootFile.rootFilePath)
        } catch (e: Exception) {
            Timber.e(e, "Missing File: %s", container.rootFile.rootFilePath)
            return null
        }

        xmlParser.parseXml(documentData.inputStream())

        val epubVersion = xmlParser.root().attributes["version"]!!.toDouble()
        val opfParser = OPFParser(container.rootFile.rootFilePath)
        val publication = opfParser.parseOpf(xmlParser, epubVersion)
                ?: return null

        val drm = container.scanForDrm()

        parseEncryption(container, publication, drm)

//        val fetcher = Fetcher(publication, container)
        parseNavigationDocument(container, publication)
        parseNcxDocument(container, publication)


        /*
         * This might need to be moved as it's not really about parsing the Epub
         * but it sets values needed (in UserSettings & ContentFilter)
         */
        setLayoutStyle(publication)

        container.drm = drm
        return PubBox(publication, container)
    }

    private fun getRootFilePath(data: ByteArray): String {
        val xmlParser = XmlParser()
        xmlParser.parseXml(data.inputStream())
        return xmlParser.getFirst("container")
                ?.getFirst("rootfiles")
                ?.getFirst("rootfile")
                ?.attributes?.get("full-path")
                ?: "content.opf"
    }

    private fun setLayoutStyle(publication: Publication) {
        var langType = LangType.other

        langTypeLoop@ for (lang in publication.metadata.languages) {
            when (lang) {
                "zh", "ja", "ko" -> {
                    langType = LangType.cjk
                    break@langTypeLoop
                }
                "ar", "fa", "he" -> {
                    langType = LangType.afh
                    break@langTypeLoop
                }
            }
        }

        val pageDirection = publication.metadata.direction
        val contentLayoutStyle = publication.metadata.contentLayoutStyle(langType, pageDirection)

        publication.cssStyle = contentLayoutStyle.name

        userSettingsUIPreset[ContentLayoutStyle.layout(publication.cssStyle!!)]?.let {
            publication.userSettingsUIPreset =
                if (publication.type == Publication.TYPE.WEBPUB) forceScrollPreset
                else it
        }
    }

    private fun fillEncryptionProfile(publication: Publication, drm: Drm?): Publication {
        drm?.let {
            for (link in publication.resources) {
                link.properties.encryption?.apply {
                    if (scheme == it.scheme) profile = it.profile
                }
            }
            for (link in publication.readingOrder) {
                link.properties.encryption?.apply {
                    if (scheme == it.scheme) profile = it.profile
                }
            }
        }
        return publication
    }

    private fun parseEncryption(container: EpubContainer, publication: Publication, drm: Drm?) {
        val documentData = try {
            container.data(encryptionDotXmlPath)
        } catch (e: Exception) {
            return
        }
        val document = XmlParser()
        document.parseXml(documentData.inputStream(), true)
        val encryptedDataElements = document.getFirst("encryption")?.get("EncryptedData") ?: return
        val encp = EncryptionParser()
        for (encryptedDataElement in encryptedDataElements) {
            val encryption = Encryption()
            val keyInfoUri = encryptedDataElement
                .getFirst("KeyInfo")
                ?.getFirst("RetrievalMethod")
                ?.let { it.attributes["URI"] }
            if (keyInfoUri == "license.lcpl#/encryption/content_key" && drm?.brand == Drm.Brand.Lcp)
                encryption.scheme = Drm.Scheme.Lcp
            encryption.algorithm = encryptedDataElement
                .getFirst("EncryptionMethod")
                ?.let { it.attributes["Algorithm"] }
            encp.parseEncryptionProperties(encryptedDataElement, encryption)
            encp.add(encryption, publication, encryptedDataElement)
        }
    }

    private fun parseNavigationDocument(container: EpubContainer, publication: Publication) {
        val navLink = publication.linkWithRel("contents") ?: return

        val navDocument = try {
            container.xmlDocumentForResource(navLink)
        } catch (e: Exception) {
            Timber.e(e, "Navigation parsing")
            return
        }

        val navByteArray = try {
            container.xmlAsByteArray(navLink)
        } catch (e: Exception) {
            Timber.e(e, "Navigation parsing")
            return
        }
        val navigationDocumentPath = navLink.href ?: return
        val ndp = NavigationDocumentParser(navigationDocumentPath)
        publication.apply {
            tableOfContents.plusAssign(ndp.tableOfContent(navByteArray))
            landmarks.plusAssign(ndp.landmarks(navDocument))
            listOfAudioFiles.plusAssign(ndp.listOfAudiofiles(navDocument))
            listOfIllustrations.plusAssign(ndp.listOfIllustrations(navDocument))
            listOfTables.plusAssign(ndp.listOfTables(navDocument))
            listOfVideos.plusAssign(ndp.listOfVideos(navDocument))
            pageList.plusAssign(ndp.pageList(navDocument))
        }
    }

    private fun parseNcxDocument(container: EpubContainer, publication: Publication) {
        val ncxLink = publication.resources.firstOrNull { it.typeLink == "application/x-dtbncx+xml" }
                ?: return
        val ncxDocument = try {
            container.xmlDocumentForResource(ncxLink)
        } catch (e: Exception) {
            Timber.e(e, "Ncx parsing")
            return
        }
        val ncxDocumentPath = ncxLink.href ?: return
        val ncxp = NCXParser(ncxDocumentPath)
        publication.apply {
            if (tableOfContents.isEmpty())
                tableOfContents.plusAssign(ncxp.tableOfContents(ncxDocument))
            if (pageList.isEmpty())
                pageList.plusAssign(ncxp.pageList(ncxDocument))
        }
    }
}
