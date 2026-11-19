package com.azuredevid.cassy.ui.catalog.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import java.text.NumberFormat

@Composable
fun CatalogProductManagementRow(
    product: CatalogProduct,
    rupiahFormat: NumberFormat,
    canManage: Boolean,
    isUploading: Boolean,
    onToggleAvailability: () -> Unit,
    onEdit: () -> Unit,
    onUploadPhoto: () -> Unit,
    onManageVariants: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassyCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        contentPadding = CassyDimens.SpaceMd
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < 560.dp
            if (isCompact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                ) {
                    CatalogProductHeaderInfo(
                        product = product,
                        rupiahFormat = rupiahFormat,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CatalogProductActionControls(
                        isAvailable = product.isAvailable,
                        canManage = canManage,
                        isUploading = isUploading,
                        onToggleAvailability = onToggleAvailability,
                        onUploadPhoto = onUploadPhoto,
                        onManageVariants = onManageVariants,
                        onEdit = onEdit,
                        onArchive = onArchive,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
                ) {
                    CatalogProductHeaderInfo(
                        product = product,
                        rupiahFormat = rupiahFormat,
                        modifier = Modifier.weight(1f)
                    )
                    CatalogProductActionControls(
                        isAvailable = product.isAvailable,
                        canManage = canManage,
                        isUploading = isUploading,
                        onToggleAvailability = onToggleAvailability,
                        onUploadPhoto = onUploadPhoto,
                        onManageVariants = onManageVariants,
                        onEdit = onEdit,
                        onArchive = onArchive
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogProductHeaderInfo(
    product: CatalogProduct,
    rupiahFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
    ) {
        val hasPhoto = product.imageUrl.isNotBlank()
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(CassyDimens.RadiusBadge))
                .background(
                    if (hasPhoto) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasPhoto) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.HideImage,
                    contentDescription = stringResource(R.string.catalog_photo_missing),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                CassyStatusBadge(
                    text = if (product.isAvailable) {
                        stringResource(R.string.catalog_status_available)
                    } else {
                        stringResource(R.string.catalog_status_sold_out)
                    },
                    tone = if (product.isAvailable) CassyToastTone.SUCCESS else CassyToastTone.WARNING
                )
            }
            val variantLabel = pluralStringResource(
                R.plurals.catalog_variant_count,
                product.variants.size,
                product.variants.size
            )
            Text(
                text = stringResource(
                    R.string.catalog_meta_format,
                    rupiahFormat.format(product.basePriceIdr),
                    product.prepStation.uppercase(),
                    variantLabel
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (product.description.isNotBlank()) {
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogProductActionControls(
    isAvailable: Boolean,
    canManage: Boolean,
    isUploading: Boolean,
    onToggleAvailability: () -> Unit,
    onUploadPhoto: () -> Unit,
    onManageVariants: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(
                    minWidth = CassyDimens.TouchTargetMin,
                    minHeight = CassyDimens.TouchTargetMin
                )
                .semantics(mergeDescendants = true) {},
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = isAvailable,
                onCheckedChange = { onToggleAvailability() }
            )
        }

        if (canManage) {
            CassySecondaryButton(
                text = stringResource(R.string.catalog_btn_upload_photo),
                onClick = onUploadPhoto,
                enabled = !isUploading,
                icon = Icons.Rounded.PhotoCamera
            )
            CassySecondaryButton(
                text = stringResource(R.string.catalog_btn_variants),
                onClick = onManageVariants,
                icon = Icons.Rounded.Tune
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.defaultMinSize(
                    minWidth = CassyDimens.TouchTargetMin,
                    minHeight = CassyDimens.TouchTargetMin
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.catalog_edit_product)
                )
            }
            IconButton(
                onClick = onArchive,
                modifier = Modifier.defaultMinSize(
                    minWidth = CassyDimens.TouchTargetMin,
                    minHeight = CassyDimens.TouchTargetMin
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.catalog_btn_archive)
                )
            }
        }
    }
}
