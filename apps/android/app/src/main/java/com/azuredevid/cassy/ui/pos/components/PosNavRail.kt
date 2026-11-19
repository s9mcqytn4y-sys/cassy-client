package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone

/**
 * Panel 1: Left Navigation Rail (category filter tabs, LAN status badge, quick utility navigation).
 */
@Composable
fun PosNavRail(
    railWidth: Dp,
    categories: List<Pair<String, String>>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    allProducts: List<CatalogProduct>,
    isPrinterOnline: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToKds: () -> Unit = {},
    onNavigateToTables: () -> Unit = {},
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoDesc = stringResource(R.string.a11y_cassy_logo)

    Surface(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(CassyDimens.SpaceSm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_cassy_monogram),
                    contentDescription = logoDesc,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(CassyDimens.RadiusButton))
                        .semantics { contentDescription = logoDesc }
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = CassyDimens.SpaceXs),
                    color = MaterialTheme.colorScheme.outline
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories, key = { (catId, _) -> catId }) { (catId, label) ->
                        val isSelected = selectedCategoryId == catId
                        val itemCount = if (catId == "all") {
                            allProducts.size
                        } else {
                            allProducts.count { it.categoryId == catId }
                        }

                        Surface(
                            shape = RoundedCornerShape(CassyDimens.RadiusButton),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = if (isSelected) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = { onCategorySelected(catId) }
                                )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(
                                    vertical = CassyDimens.SpaceXs,
                                    horizontal = CassyDimens.SpaceXs
                                )
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (itemCount > 0) {
                                    Text(
                                        text = stringResource(R.string.catalog_category_count, itemCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs),
                modifier = Modifier.fillMaxWidth()
            ) {
                CassyStatusBadge(
                    text = if (isPrinterOnline) {
                        stringResource(R.string.lan_status_ok)
                    } else {
                        stringResource(R.string.lan_status_off)
                    },
                    tone = if (isPrinterOnline) CassyToastTone.SUCCESS else CassyToastTone.ERROR,
                    modifier = Modifier.fillMaxWidth()
                )

                CassySecondaryButton(
                    text = stringResource(R.string.catalog_nav_label),
                    onClick = onNavigateToCatalog,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    modifier = Modifier.fillMaxWidth()
                )

                CassySecondaryButton(
                    text = stringResource(R.string.kds_nav_label),
                    onClick = onNavigateToKds,
                    icon = Icons.Rounded.SoupKitchen,
                    modifier = Modifier.fillMaxWidth()
                )

                CassySecondaryButton(
                    text = stringResource(R.string.tables_nav_label),
                    onClick = onNavigateToTables,
                    icon = Icons.Rounded.TableRestaurant,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        shape = RoundedCornerShape(CassyDimens.RadiusButton),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_byok_settings),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToPairing,
                        shape = RoundedCornerShape(CassyDimens.RadiusButton),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = CassyDimens.TouchTargetMin)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_pair_device),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
