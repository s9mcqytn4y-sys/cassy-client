package com.azuredevid.cassy.ui.shift.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.ui.designsystem.CassyDangerButton
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.theme.statusOkBg
import com.azuredevid.cassy.ui.theme.statusOkFg
import com.azuredevid.cassy.ui.theme.statusWarnBg
import com.azuredevid.cassy.ui.theme.statusWarnFg
import java.text.NumberFormat
import java.util.Locale

private val ShiftBannerCompactBreakpoint = 600.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftStatusBanner(
    isShiftOpen: Boolean,
    currentShift: Shift?,
    onOpenShiftClick: () -> Unit,
    onXReportClick: () -> Unit = {},
    onCashMovementClick: () -> Unit,
    onCloseShiftClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFormatter = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"))

    if (isShiftOpen && currentShift != null) {
        val okFg = statusOkFg()
        val shortId = if (currentShift.id.length > 6) {
            currentShift.id.take(6).uppercase()
        } else {
            currentShift.id.uppercase()
        }
        val cashierName = currentShift.openedByName.ifBlank {
            stringResource(R.string.shift_banner_cashier_default)
        }
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
            shape = RoundedCornerShape(CassyDimens.RadiusCard),
            color = statusOkBg(),
            border = BorderStroke(1.dp, okFg.copy(alpha = 0.28f))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm)
            ) {
                val isCompact = maxWidth < ShiftBannerCompactBreakpoint
                val infoContent: @Composable (Modifier) -> Unit = { infoModifier ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                        modifier = infoModifier
                    ) {
                        CassyStatusBadge(
                            text = stringResource(R.string.shift_banner_id_format, shortId),
                            tone = CassyToastTone.SUCCESS,
                            icon = Icons.Rounded.AccountBalanceWallet
                        )

                        Column {
                            Text(
                                text = stringResource(R.string.shift_banner_cashier_label, cashierName),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = okFg
                            )
                            if (currentShift.expectedCash != null) {
                                Text(
                                    text = stringResource(
                                        R.string.shift_banner_expected_cash,
                                        rupiahFormatter.format(currentShift.expectedCash)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = okFg.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
                val actionButtons: @Composable () -> Unit = {
                    CassySecondaryButton(
                        text = stringResource(R.string.shift_banner_xreport_btn),
                        onClick = onXReportClick,
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                    CassySecondaryButton(
                        text = stringResource(R.string.shift_banner_movement_btn),
                        onClick = onCashMovementClick,
                        icon = Icons.Rounded.SwapHoriz,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                    CassyDangerButton(
                        text = stringResource(R.string.shift_banner_close_btn),
                        onClick = onCloseShiftClick
                    )
                }

                if (isCompact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        infoContent(Modifier.fillMaxWidth())
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs),
                            verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                        ) {
                            actionButtons()
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        infoContent(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)) {
                            actionButtons()
                        }
                    }
                }
            }
        }
    } else {
        val warnFg = statusWarnFg()
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceXs),
            shape = RoundedCornerShape(CassyDimens.RadiusCard),
            color = statusWarnBg(),
            border = BorderStroke(1.dp, warnFg.copy(alpha = 0.28f))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CassyDimens.SpaceMd, vertical = CassyDimens.SpaceSm)
            ) {
                val isCompact = maxWidth < ShiftBannerCompactBreakpoint
                val closedInfo: @Composable (Modifier) -> Unit = { infoModifier ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm),
                        modifier = infoModifier
                    ) {
                        CassyStatusBadge(
                            text = stringResource(R.string.shift_banner_closed_badge),
                            tone = CassyToastTone.WARNING
                        )
                        Text(
                            text = stringResource(R.string.shift_banner_closed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = warnFg
                        )
                    }
                }
                val openButton: @Composable () -> Unit = {
                    CassyPrimaryButton(
                        text = stringResource(R.string.shift_banner_open_btn),
                        onClick = onOpenShiftClick,
                        icon = Icons.Rounded.LockOpen
                    )
                }

                if (isCompact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
                    ) {
                        closedInfo(Modifier.fillMaxWidth())
                        openButton()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        closedInfo(Modifier.weight(1f))
                        openButton()
                    }
                }
            }
        }
    }
}
