package app.scifin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected as selectedState
import androidx.compose.ui.semantics.semantics
import app.scifin.ui.LocalTokens
import app.scifin.ui.Lucide
import app.scifin.ui.LucideIcon

/** The prototype's `<select>` in the `.field` shape: the chosen label with a chevron, a menu of the options. */
@Composable
fun Dropdown(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current; val p = t.palette
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = t.spacing.target).background(p.surf, RoundedCornerShape(t.radii.md)).border(t.strokes.ctl, p.bordStrong, RoundedCornerShape(t.radii.md))
                .clickable(role = Role.DropdownList) { open = true }.padding(horizontal = t.spacing.u * 1.5f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: "", color = p.txt, fontSize = t.type.body, modifier = Modifier.weight(1f))
            LucideIcon(Lucide.CHEVRON_RIGHT, tint = p.txt2, size = t.icons.md, modifier = Modifier.rotate(90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((id, label) in options) DropdownMenuItem(
                text = { Text(label, color = p.txt, fontSize = t.type.body) }, onClick = { onSelect(id); open = false },
                modifier = Modifier.semantics { selectedState = id == selected },
            )
        }
    }
}
