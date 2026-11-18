package com.unhappychoice.norimaki.infrastructure.preference

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class APITokenBackupTest : DescribeSpec({
    val application = readXml("AndroidManifest.xml").child("application")
    val androidNamespace = "http://schemas.android.com/apk/res/android"

    describe("API token backup rules") {
        it("excludes the token preference from Android 11 and earlier backups and transfers") {
            val reference = application.getAttributeNS(androidNamespace, "fullBackupContent")
            val rules = readRules(reference, "full-backup-content")

            rules.verifyTokenExclusions()
        }

        it("excludes the token preference from Android 12 and later cloud backups and device transfers") {
            val reference = application.getAttributeNS(androidNamespace, "dataExtractionRules")
            val rules = readRules(reference, "data-extraction-rules")

            rules.child("cloud-backup").verifyTokenExclusions()
            rules.child("device-transfer").verifyTokenExclusions()
        }
    }
})

private fun readRules(reference: String, root: String): Element {
    require(reference.startsWith("@xml/"))
    return readXml("res/${reference.removePrefix("@")}.xml").also { it.tagName shouldBe root }
}

private fun readXml(path: String): Element =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File("src/main", path)).documentElement

private fun Element.child(tag: String): Element = children(tag).single()

private fun Element.children(tag: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
        .filter { it.tagName == tag }

private fun Element.verifyTokenExclusions() {
    val excludedPreferences = children("exclude")
        .filter { it.getAttribute("domain") == "sharedpref" }
        .map { it.getAttribute("path") }

    excludedPreferences.shouldContainAll(
        "$API_TOKEN_PREFERENCE_NAME.xml",
        "$API_TOKEN_PREFERENCE_NAME.xml.bak"
    )
}
