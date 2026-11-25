package com.logie.blizz.warsmash

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportedMapsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun copiesMapAndLeavesStockMapsAlone() {
        val game = tmp.newFolder()
        val stock = File(ImportedMaps.directory(game), "stock.w3x")
        stock.parentFile.mkdirs()
        stock.writeText("stock")
        val content = "HM3Wsample".toByteArray()
        val map = ImportedMaps.import(game, "DotA.w3x", content.inputStream())
        assertArrayEquals(content, map.readBytes())
        assertEquals(listOf(map), ImportedMaps.list(game))
        assertFalse(ImportedMaps.delete(game, stock))
        assertTrue(ImportedMaps.delete(game, map))
        assertTrue(stock.exists())
    }

    @Test fun rejectsWrongExtensionAndTraversal() {
        for (name in listOf("test.zip", "../map.w3x", "a/b.w3x")) {
            assertTrue(runCatching { ImportedMaps.fileName(name) }.isFailure)
        }
    }

    @Test fun refusesInvalidHeaderAndDuplicate() {
        val game = tmp.newFolder()
        assertTrue(runCatching { ImportedMaps.import(game, "bad.w3x", "not a map".byteInputStream()) }.isFailure)
        val map = ImportedMaps.import(game, "test.w3x", "HM3Woriginal".byteInputStream())
        assertTrue(runCatching { ImportedMaps.import(game, "test.w3x", "HM3Wreplacement".byteInputStream()) }.isFailure)
        assertEquals("HM3Woriginal", map.readText())
        assertEquals(listOf(map), ImportedMaps.list(game))
    }
}
