package com.lostf1sh.pixelplayeross

import androidx.annotation.StringRes
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue

/** Reads `<string>` entries straight from src/main/res so JVM tests can check every translation. */
internal object LocaleStringResources {

    // Translation folders such as values-ru or values-zh-rCN, not qualifiers like values-night.
    private val LOCALE_DIR = Regex("""values-[a-z]{2,3}(-r[A-Z]{2})?""")
    private val STRING_ENTRY = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")

    /** `values` plus every translation folder. */
    fun localeFolders(): List<String> {
        val folders = resDirectory().listFiles { file ->
            file.isDirectory && (file.name == "values" || LOCALE_DIR.matches(file.name))
        }.orEmpty().map { it.name }.sorted()
        assertTrue(folders.size > 1, "No locale folders found under ${resDirectory()}")
        return folders
    }

    /** Raw entries of [folder], keyed by name, with XML escapes such as `\'` left in place. */
    fun stringsIn(folder: String): Map<String, String> {
        val xml = File(resDirectory(), folder).listFiles { file -> file.extension == "xml" }.orEmpty()
            .joinToString("\n") { it.readText() }
        return STRING_ENTRY.findAll(xml).associate { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
    }

    fun nameOf(@StringRes id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    fun assertTranslatedEverywhere(names: List<String>) {
        localeFolders().forEach { folder ->
            val strings = stringsIn(folder)
            names.forEach { name ->
                assertTrue(!strings[name].isNullOrEmpty(), "$folder has no text for $name")
            }
        }
    }

    private fun resDirectory(): File = sequenceOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).first { it.isDirectory }
}
