/*
 * Module: r2-streamer-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.streamer.parser.epub

import org.readium.r2.shared.*
import org.readium.r2.shared.parser.xml.Node

//const val noTitleError = "Error : Publication has no title"

class MetadataParser {
    fun parseRenditionProperties(metadataElement: Node, metadata: Metadata) {
        val metas = metadataElement.get("meta")!!
        if (metas.isEmpty()) {
            metadata.rendition.layout = RenditionLayout.Reflowable
            return
        }

        metas.firstOrNull { it.attributes["property"] == "rendition:layout" }?.text?.let {
            metadata.rendition.layout = RenditionLayout.fromString(it)
        }?: run {
            metadata.rendition.layout = RenditionLayout.Reflowable
        }
        metas.firstOrNull { it.attributes["property"] == "rendition:flow" }?.text?.let {
            metadata.rendition.flow = RenditionFlow.fromString(it)
        }
        metas.firstOrNull { it.attributes["property"] == "rendition:orientation" }?.text?.let {
            metadata.rendition.orientation = RenditionOrientation.fromString(it)
        }
        metas.firstOrNull { it.attributes["property"] == "rendition:spread" }?.text?.let {
            metadata.rendition.spread = RenditionSpread.fromString(it)
            if (it == "portrait") {
                metadata.rendition.spread = RenditionSpread.Both
            }
        }
        metas.firstOrNull { it.attributes["property"] == "rendition:viewport" }?.text?.let {
            metadata.rendition.viewport = it
        }
    }

    /** Parse and return the main title informations of the publication the from
     * the OPF XML document `<metadata>` element.
     * In the simplest cases it just return the value of the <dc:title> XML
     * element, but sometimes there are alternative titles (titles in others
     * languages).
     * See `MultilanguageString` for complementary informations.
     *
     * @param metadata: The `<metadata>` element.
     * @return: The content of the `<dc:title>` element, `null` if the element wasn't found */
    fun mainTitle(metadata: Node): MultilanguageString? {
        val titles = metadata.children.filter { it.name == "dc:title" }
        if (titles.isEmpty())
            return null //throw Exception(noTitleError)
        val multilanguageTitle = MultilanguageString()

        multilanguageTitle.singleString =
            metadata.getFirst("dc:title")?.text
                ?: return null //throw Exception(noTitleError)

        val mainTitle = getMainTitleElement(titles, metadata) ?: return multilanguageTitle
        multilanguageTitle.multiString = multiString(mainTitle, metadata).toMutableMap()
        return multilanguageTitle
    }

    /** Parse and return the Epub unique identifier.
     *
     * @param metadata: The metadata XML element.
     * @return The content of the `<dc:identifier>` element, `null` if the element wasn't found */
    fun uniqueIdentifier(metadata: Node, documentProperties: Map<String, String>): String? {
        val identifiers = metadata.get("dc:identifier") //?: throw Exception("No identifier")
        if (identifiers.isNullOrEmpty())
            return null
        val uniqueId = documentProperties["unique-identifier"]
        if (identifiers.size > 1 && uniqueId != null) {
            val uniqueIdentifier = identifiers.firstOrNull { it.attributes["id"] == uniqueId }
            if (uniqueIdentifier != null) //?: throw Exception("No identifier")
                return uniqueIdentifier.text
        }
        return identifiers[0].text
    }

    fun modifiedDate(metadataElement: Node): String? = metadataElement.children.firstOrNull {
        it.name == "meta" &&
        it.attributes["property"] == "dcterms:modified"
    }?.text

    fun subject(metadataElement: Node): Subject? {
        val subjectElement = metadataElement.getFirst("dc:subject") ?: return null
        val name = subjectElement.text ?: return null
        with(Subject()) {
            this.name = name
            scheme = subjectElement.attributes["opf:authority"]
            code = subjectElement.attributes["opf:term"]
            return this
        }
    }

    fun parseContributors(metadataElement: Node, metadata: Metadata, epubVersion: Double) {
        val allContributors = mutableListOf<Node>().apply {
            addAll(findContributorsXmlElements(metadataElement))
            if (epubVersion == 3.0)
                addAll(findContributorsMetaXmlElements(metadataElement))
        }
        allContributors.forEach {
            parseContributor(it, metadataElement, metadata)
        }
    }

    private fun parseContributor(element: Node, metadataElement: Node, metadata: Metadata) {
        val contributor = createContributor(element, metadataElement)

        val eid = element.attributes["id"]
        if (eid != null) {
            for (it in metadataElement.children) {
                if (it.name != "meta" ||
                    it.attributes["refines"] != eid ||
                    it.attributes["property"] != "role"
                ) continue

                it.text?.let { text -> contributor.roles.add(text) }
            }
        }

        if (contributor.roles.isNotEmpty()) {
            for (role in contributor.roles) {
                when (role) {
                    "aut" -> metadata.authors.add(contributor)
                    "trl" -> metadata.translators.add(contributor)
                    "art" -> metadata.artists.add(contributor)
                    "edt" -> metadata.editors.add(contributor)
                    "ill" -> metadata.illustrators.add(contributor)
                    "clr" -> metadata.colorists.add(contributor)
                    "nrt" -> metadata.narrators.add(contributor)
                    "pbl" -> metadata.publishers.add(contributor)
                    else -> metadata.contributors.add(contributor)
                }
            }
        } else {
            if (element.name == "dc:creator" || element.attributes["property"] == "dcterms:contributor") {
                metadata.authors.add(contributor)
            } else if (element.name == "dc:publisher" || element.attributes["property"] == "dcterms:publisher") {
                metadata.publishers.add(contributor)
            } else {
                metadata.contributors.add(contributor)
            }
        }
    }

    private fun createContributor(element: Node, metadata: Node): Contributor {
        val contributor = Contributor()
        contributor.multilanguageName.singleString = element.text
        contributor.multilanguageName.multiString = multiString(element, metadata).toMutableMap()
        element.attributes["opf:role"]?.let { contributor.roles.add(it) }
        element.attributes["opf:file-as"]?.let { contributor.sortAs = it }
        return contributor
    }

    fun parseMediaDurations(metadataElement: Node, otherMetadata: MutableList<MetadataItem>): MutableList<MetadataItem> {
        val ret = ArrayList<MetadataItem>(otherMetadata)
        for (it in metadataElement.children) {
            if (it.name != "meta" ||
                it.attributes["property"] != "media:duration")
                continue

            val item = MetadataItem()
            item.property = it.attributes["refines"]
            item.value = it.text
            // FIXME old code assign ret = otherMetadata.plus(item).toMutableList()
            // so, it will actually add only the last item in the for loop
            ret.add(item)
        }
        return ret
    }

    /** Return the XML element corresponding to the main title (title having
     * `<meta refines="#.." property="title-type" id="title-type">main</meta>`
     * @param titles: The titles XML elements array.
     * @param metadata: The Publication Metadata XML object.
     * @return The main title XML element. */
    private fun getMainTitleElement(titles: List<Node>, metadata: Node): Node? {
        for (title in titles) {
            if (title.attributes["id"] == null)
                continue
            for (meta in metadata.children) {
                if (meta.name == "meta"
                    && meta.attributes["refines"] == "#${title.attributes["id"]}"
                    && meta.attributes["property"] == "title-type"
                    && meta.text == "main"
                ) return meta
            }
        }
        return null
    }

    private fun findContributorsXmlElements(metadata: Node): List<Node> {
        return metadata.children.filter {
            it.name == "dc:publisher" ||
            it.name == "dc:creator" ||
            it.name == "dc:contributor"
        }
//        val allContributors: MutableList<Node> = mutableListOf()
//        metadata.children.filterTo(allContributors){ it.name == "dc:publisher" }
//        metadata.children.filterTo(allContributors){ it.name == "dc:creator" }
//        metadata.children.filterTo(allContributors){ it.name == "dc:contributor" }
//        return allContributors
    }

    private fun findContributorsMetaXmlElements(metadata: Node): MutableList<Node> {
        return metadata.children.filterTo(ArrayList()) {
            it.name == "meta" &&
                it.attributes["property"].let { p ->
                    p == "dcterms:publisher" ||
                    p == "dcterms:creator" ||
                    p == "dcterms:contributor"
                }
        }
    }

    /** Return an array of lang:string, defining the multiple representations of
     * a string in different languages.
     * @param element: The element to parse (can be a title or a contributor).
     * @param metadata: The metadata XML element. */
    private fun multiString(element: Node, metadata: Node): Map<String, String> {
        val multiString: MutableMap<String, String> = mutableMapOf()

        val elementId = element.attributes["id"] ?: return multiString

        for(it in metadata.children) {
            if (it.name != "meta")
                continue
            if (it.attributes["refines"] != "#$elementId"
                || it.attributes["property"] != "alternate-script")
                continue
            // `it` is now an altScriptMeta
            val title = it.text
            val lang = it.attributes["xml:lang"]
            if (title != null && lang != null)
                multiString[lang] = title
        }
        if (multiString.isNotEmpty()) {
            val publicationDefaultLanguage = metadata.getFirst("dc:language")?.text
                    ?: throw Exception("No language")
            val lang = element.attributes["xml:lang"] ?: publicationDefaultLanguage
            val value = element.text ?: ""
            multiString[lang] = value
        }
        return multiString
    }
}
