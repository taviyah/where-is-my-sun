package com.wheresmysun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wheresmysun.SkyViewModel
import com.wheresmysun.astro.GeoPoint
import com.wheresmysun.data.EinkMode
import java.util.Locale

/** Pick GPS or a saved place, save the current spot, or add one by coordinates. */
@Composable
fun PlacesDialog(vm: SkyViewModel, onDismiss: () -> Unit) {
    var adding by remember { mutableStateOf<GeoPoint?>(null) }
    var addingManual by remember { mutableStateOf(false) }
    val gps = vm.locationTracker.location

    if (adding != null || addingManual) {
        AddPlaceDialog(
            initial = adding,
            onDismiss = { adding = null; addingManual = false },
            onSave = { name, point ->
                vm.addPlace(name, point)
                adding = null
                addingManual = false
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlaceRow(
                    title = "Current location",
                    subtitle = gps?.let { String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude) } ?: "Waiting for GPS…",
                    selected = vm.selectedPlaceId == null,
                    onClick = { vm.selectPlace(null); onDismiss() },
                )
                for (place in vm.places) {
                    PlaceRow(
                        title = place.name,
                        subtitle = String.format(Locale.US, "%.4f, %.4f", place.point.latitude, place.point.longitude),
                        selected = vm.selectedPlaceId == place.id,
                        onClick = { vm.selectPlace(place.id); onDismiss() },
                        onDelete = { vm.deletePlace(place.id) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(
                    enabled = gps != null,
                    onClick = { gps?.let { adding = GeoPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else 0.0) } },
                ) {
                    Icon(Icons.Outlined.BookmarkAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save this spot (e.g. where you parked)")
                }
                TextButton(onClick = { addingManual = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a place by coordinates")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PlaceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete $title") }
        }
    }
}

/** Name a place; when [initial] is null the user types coordinates too. */
@Composable
fun AddPlaceDialog(initial: GeoPoint?, onDismiss: () -> Unit, onSave: (String, GeoPoint) -> Unit) {
    var name by remember { mutableStateOf(if (initial != null) "Parking spot" else "") }
    var lat by remember { mutableStateOf(initial?.latitude?.let { String.format(Locale.US, "%.5f", it) } ?: "") }
    var lon by remember { mutableStateOf(initial?.longitude?.let { String.format(Locale.US, "%.5f", it) } ?: "") }
    val latValue = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lonValue = lon.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val valid = latValue != null && lonValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Save this spot" else "Add a place") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (initial == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        lat, { lat = it }, label = { Text("Latitude (e.g. 32.0853)") }, singleLine = true,
                        isError = lat.isNotEmpty() && latValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        lon, { lon = it }, label = { Text("Longitude (e.g. 34.7818)") }, singleLine = true,
                        isError = lon.isNotEmpty() && lonValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tip: long-press a spot in Google Maps to copy its coordinates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(name.trim(), GeoPoint(latValue!!, lonValue!!, initial?.elevation ?: 0.0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(vm: SkyViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                SettingSwitch("Show the moon", s.showMoon) { vm.updateSettings(s.copy(showMoon = it)) }
                SettingSwitch("Hour marks on the paths", s.showHourLabels) { vm.updateSettings(s.copy(showHourLabels = it)) }
                SettingSwitch("Highlight golden hour", s.highlightGoldenHour) { vm.updateSettings(s.copy(highlightGoldenHour = it)) }
                SettingSwitch("Sky map turns with the phone", s.mapFollowsCompass) { vm.updateSettings(s.copy(mapFollowsCompass = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("E-ink screen", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (vm.isEinkDevice) "This looks like an e-ink phone." else "Black-and-white look with fewer screen refreshes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(EinkMode.AUTO to "Auto", EinkMode.ON to "On", EinkMode.OFF to "Off")
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.einkMode == mode,
                            onClick = { vm.updateSettings(s.copy(einkMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
                if (vm.alignmentDeg != 0.0) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = vm::resetAlignment) {
                        Text(String.format(Locale.getDefault(), "Reset manual alignment (%.1f°)", vm.alignmentDeg))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
