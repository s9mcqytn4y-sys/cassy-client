package com.azuredevid.cassy.ui.kds.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton

/** Station switch (Barista or Hot Kitchen); only stations the session may read are offered. */
@Composable
fun KdsStationSelector(
    stations: List<KdsStation>,
    selected: KdsStation?,
    onSelect: (KdsStation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (stations.size < 2) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
        stations.forEach { station ->
            val label = stringResource(station.labelRes())
            val icon = if (station == KdsStation.BAR) Icons.Rounded.LocalCafe else Icons.Rounded.SoupKitchen
            if (station == selected) {
                CassyPrimaryButton(text = label, icon = icon, onClick = {})
            } else {
                CassySecondaryButton(text = label, icon = icon, onClick = { onSelect(station) })
            }
        }
    }
}

fun KdsStation.labelRes(): Int = when (this) {
    KdsStation.BAR -> R.string.kds_station_bar
    KdsStation.KITCHEN -> R.string.kds_station_kitchen
}
