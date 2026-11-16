package com.azuredevid.cassy.ui.tables.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.util.QrBitmapGenerator
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDangerButton
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextActionButton
import com.azuredevid.cassy.ui.designsystem.CassyTonalButton
import com.azuredevid.cassy.ui.designsystem.CassyToastTone

/**
 * Right pane (32 %) for the selected table: QR preview and the poster, PDF, rotate, edit, and archive
 * actions. QR content and actions appear only when [qrUrl] is non-null, which requires
 * `table.qr.manage`; edit and archive require [canManageTables].
 */
@Composable
fun TableQrPanel(
    table: TableEntry,
    qrUrl: String?,
    canManageTables: Boolean,
    isBusy: Boolean,
    isPrinting: Boolean,
    isExporting: Boolean,
    onPrintPoster: () -> Unit,
    onExportPdf: () -> Unit,
    onRotateQr: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    CassyCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceMd)
        ) {
            Text(
                text = table.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TableStatusBadge(table)

            if (qrUrl != null) {
                QrPreview(url = qrUrl, tableName = table.displayName)
                Text(
                    text = stringResource(R.string.tables_qr_rotated_at, table.qrRotatedAt.take(10)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CassyPrimaryButton(
                    text = stringResource(R.string.tables_btn_print_poster),
                    icon = Icons.Rounded.Print,
                    onClick = onPrintPoster,
                    enabled = !isBusy,
                    loading = isPrinting,
                    modifier = Modifier.fillMaxWidth()
                )
                CassyTonalButton(
                    text = stringResource(R.string.tables_btn_export_pdf),
                    icon = Icons.Rounded.PictureAsPdf,
                    onClick = onExportPdf,
                    enabled = !isBusy,
                    loading = isExporting,
                    modifier = Modifier.fillMaxWidth()
                )
                CassySecondaryButton(
                    text = stringResource(R.string.tables_btn_rotate_qr),
                    icon = Icons.Rounded.QrCode2,
                    onClick = onRotateQr,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CassyStatusBadge(
                    text = stringResource(R.string.tables_qr_hidden),
                    tone = CassyToastTone.INFO,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (canManageTables) {
                CassyTextActionButton(
                    text = stringResource(R.string.tables_btn_edit),
                    icon = Icons.Rounded.Edit,
                    onClick = onEdit,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                CassyDangerButton(
                    text = stringResource(R.string.tables_btn_archive),
                    icon = Icons.Rounded.Archive,
                    onClick = onArchive,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** On-screen QR; the bitmap carries its own black-on-white quiet zone so it scans in every theme. */
@Composable
private fun QrPreview(url: String, tableName: String) {
    val bitmap = remember(url) { QrBitmapGenerator.createQrBitmap(url, 480, 480) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(CassyDimens.RadiusCard))
            .background(MaterialTheme.colorScheme.surface)
            .padding(CassyDimens.SpaceSm),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.tables_a11y_qr_preview, tableName),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        }
    }
}
