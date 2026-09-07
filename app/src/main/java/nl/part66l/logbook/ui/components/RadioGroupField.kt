package nl.part66l.logbook.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A short, fixed set of options shown all at once — no dropdown, no experimental
 * Material3 API. [value] may be null (nothing selected yet); `option == value`
 * handles that naturally, so one function covers both the "always has a default"
 * and "starts unset" cases.
 */
@Composable
fun <T> RadioGroupField(
    label: String,
    value: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == value,
                        onClick = { onValueChange(option) },
                        role = Role.RadioButton,
                    ),
            ) {
                RadioButton(selected = option == value, onClick = null)
                Text(optionLabel(option))
            }
        }
    }
}
