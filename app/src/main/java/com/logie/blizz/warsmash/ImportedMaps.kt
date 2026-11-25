package com.logie.blizz.warsmash

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.Locale

/** Only maps imported by this feature are listed or deleted. Existing stock/user maps are untouched. */
object ImportedMaps {
    private const val PREFIX = "Imported-"
    private const val MAX_BYTES = 256L * 1024 * 1024

    fun directory(game: File) = File(game, "Maps/Download")

    fun fileName(displayName: String): String {
        val name = displayName.trim()
        require(name.length in 5..180 && !name.startsWith(".") &&
            name.none { it.isISOControl() || it in "/\\:*?\"<>|" }) { "Invalid map filename." }
        require(name.lowercase(Locale.ROOT).let { it.endsWith(".w3x") || it.endsWith(".w3m") }) {
            "Choose a Warcraft III .w3x or .w3m map."
        }
        return PREFIX + name
    }

    fun list(game: File): List<File> = directory(game).listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith(PREFIX) &&
            it.extension.lowercase(Locale.ROOT) in setOf("w3x", "w3m") }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

    /** Header screening only; the engine is responsible for full map and script compatibility. */
    fun import(game: File, displayName: String, input: InputStream): File {
        val name = fileName(displayName)
        require(game.isDirectory) { "Install Warcraft III first." }
        val dir = directory(game)
        check(dir.isDirectory || dir.mkdirs()) { "Could not create the map folder." }
        val destination = File(dir, name)
        require(!destination.exists()) { "This map already exists. Delete the imported copy first or choose a different file." }
        val temp = File.createTempFile(".map-", ".tmp", dir)
        try {
            val header = ByteArray(4)
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                require(n > 0) { "The file is not a Warcraft III map." }
                read += n
            }
            val signature = String(header, Charsets.ISO_8859_1)
            require(signature == "HM3W" || signature == "MPQ\u001a" || signature == "MPQ\u001b") {
                "The file is not a recognised Warcraft III map archive."
            }
            temp.outputStream().use { out ->
                out.write(header)
                val buffer = ByteArray(64 * 1024)
                var total = 4L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "Map is larger than 256 MiB." }
                    out.write(buffer, 0, n)
                }
                require(total > 4) { "The map file is incomplete." }
            }
            Files.move(temp.toPath(), destination.toPath())
            return destination
        } finally {
            temp.delete()
        }
    }

    fun delete(game: File, file: File): Boolean =
        list(game).any { it.canonicalFile == file.canonicalFile } && file.delete()
}
