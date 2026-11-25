package com.logie.blizz.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.logie.blizz.importer.GameImportManager
import com.logie.blizz.importer.Warcraft3WarsmashImporter
import com.logie.blizz.warsmash.ImportedMaps
import com.logie.blizz.warsmash.WarsmashRuntime
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomMapsDialog(gameId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val game = remember(gameId) {
        Warcraft3WarsmashImporter.installDir(GameImportManager.paths(context), WarsmashRuntime.editionOf(gameId))
    }
    var files by remember(game) { mutableStateOf(game?.let(ImportedMaps::list).orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && game != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val imported = withContext(Dispatchers.IO) {
                        val resolver = context.contentResolver
                        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: error("Could not read the map filename.")
                        resolver.openInputStream(uri)?.use { ImportedMaps.import(game, name, it) }
                            ?: error("Could not open the selected map.")
                    }
                    files = ImportedMaps.list(game)
                    message = "Imported ${imported.name.removePrefix("Imported-")}. Open Warcraft III and choose Custom Game."
                } catch (e: Exception) {
                    message = e.message ?: "Map import failed."
                } finally {
                    busy = false
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Custom Maps") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(if (game == null) "Install Warcraft III first." else "Import .w3x or .w3m maps. Maps are listed in the game's Custom Game screen after reopening Warcraft III. Compatibility depends on the engine.")
                if (files.isEmpty() && game != null) Text("No imported maps yet.")
                files.forEach { file ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(file.name.removePrefix("Imported-"), Modifier.weight(1f))
                        TextButton(onClick = { deleting = file }, enabled = !busy) { Text("Delete") }
                    }
                }
                if (busy) Text("Importing map…")
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = game != null && !busy) { Text("Import Map") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } }
    )
    deleting?.let { file ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete imported map?") },
            text = { Text(file.name.removePrefix("Imported-")) },
            confirmButton = {
                TextButton(onClick = {
                    if (game != null) {
                        message = if (ImportedMaps.delete(game, file)) "Map deleted." else "Could not delete map."
                        files = ImportedMaps.list(game)
                    }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}
