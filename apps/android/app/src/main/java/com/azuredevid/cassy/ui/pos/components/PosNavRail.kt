package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PrintDisabled
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.statusErrFg

/**
 * Panel 1 of the tablet cashier: a fixed 88dp rail with the screens a cashier switches to, and the
 * printer state as text at the bottom. Categories live as chips above the catalog grid.
 */
@Composable
fun PosNavRail(
    isPrinterOnline: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToKds: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(CassyDimens.PosRailWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = CassyDimens.SpaceLg, horizontal = CassyDimens.SpaceXs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_cassy_monogram),
                    contentDescription = stringResource(R.string.a11y_cassy_logo),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(CassyDimens.RadiusButton))
                )
                RailItem(Icons.Rounded.PointOfSale, stringResource(R.string.pos_rail_cashier), selected = true) {}
                RailItem(Icons.Rounded.TableRestaurant, stringResource(R.string.pos_rail_tables), onClick = onNavigateToTables)
                RailItem(Icons.Rounded.SoupKitchen, stringResource(R.string.pos_rail_kitchen), onClick = onNavigateToKds)
                RailItem(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.catalog_nav_label), onClick = onNavigateToCatalog)
                RailItem(Icons.Rounded.Settings, stringResource(R.string.pos_rail_settings), onClick = onNavigateToSettings)
                RailItem(Icons.Rounded.QrCodeScanner, stringResource(R.string.pos_rail_pairing), onClick = onNavigateToPairing)
            }

            // Printer state is text, not only a dot, so it reads at a glance and for TalkBack.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isPrinterOnline) Icons.Rounded.Print else Icons.Rounded.PrintDisabled,
                    contentDescription = null,
                    tint = if (isPrinterOnline) MaterialTheme.colorScheme.primary else statusErrFg()
                )
                Text(
                    text = stringResource(if (isPrinterOnline) R.string.pos_rail_printer_ok else R.string.pos_rail_printer_off),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPrinterOnline) MaterialTheme.colorScheme.onSurfaceVariant else statusErrFg(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CassyDimens.RadiusButton),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = CassyDimens.SpaceSm)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
