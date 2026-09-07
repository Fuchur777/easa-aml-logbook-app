package nl.part66l.logbook.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

/** Read-only field that opens a date picker on tap — the only way in, so no invalid date text can ever be typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value?.format(DISPLAY_FORMAT).orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = { IconButton(onClick = { showDialog = true }) { Text("📅") } },
        modifier = modifier,
    )

    if (showDialog) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    onValueChange(millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
