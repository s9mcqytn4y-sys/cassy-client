package com.azuredevid.cassy.ui.settings.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.CreatedDisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayScreen
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDangerButton
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyModalDialog
import com.azuredevid.cassy.ui.designsystem.CassyPrimaryButton
import com.azuredevid.cassy.ui.designsystem.CassySecondaryButton
import com.azuredevid.cassy.ui.designsystem.CassySectionHeader
import com.azuredevid.cassy.ui.designsystem.CassyStatusBadge
import com.azuredevid.cassy.ui.designsystem.CassyTextField
import com.azuredevid.cassy.ui.designsystem.CassyToastTone
import com.azuredevid.cassy.ui.settings.DisplayScreensUiState
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Queue TV screens of the store: add one, share its link to the TV, revoke one that is lost.
 * Owner setting: moves to Cassy Manager (A3), see Docs/PRD/PrdFrontendUxAndLaunch.md section 4.
 */
@Composable
fun DisplayScreensTab(
    state: DisplayScreensUiState,
    onAdd: () -> Unit,
    onRevoke: (DisplayScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CassyDimens.SpaceXl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceLg)
    ) {
        CassyCard {
            CassySectionHeader(title = stringResource(R.string.display_tab_title), subtitle = stringResource(R.string.display_tab_desc))
        }
        state.message?.takeIf { state.newName == null }?.let {
            CassyErrorBanner(message = it.asString(), tone = if (state.messageOk == true) CassyToastTone.SUCCESS else CassyToastTone.ERROR)
        }
        CassyPrimaryButton(
            text = stringResource(R.string.display_btn_add),
            onClick = onAdd,
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.screens.isEmpty() && !state.isLoading) {
            CassyEmptyState(
                icon = Icons.Rounded.Tv,
                title = stringResource(R.string.display_empty_title),
                subtitle = stringResource(R.string.display_empty_body)
            )
        }
        state.screens.forEach { screen ->
            CassyCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = screen.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = screen.lastSeenAt?.let { stringResource(R.string.display_last_seen, dateOf(it)) }
                                ?: stringResource(R.string.display_never_seen),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (screen.isActive) {
                        CassySecondaryButton(
                            text = stringResource(R.string.display_btn_revoke),
                            onClick = { onRevoke(screen) },
                            icon = Icons.Rounded.TvOff
                        )
                    } else {
                        CassyStatusBadge(text = stringResource(R.string.display_revoked), tone = CassyToastTone.WARNING)
                    }
                }
            }
        }
    }
}

@Composable
fun NewDisplayDialog(name: String, isBusy: Boolean, error: String?, onName: (String) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit) {
    CassyModalDialog(
        title = stringResource(R.string.display_btn_add),
        icon = Icons.Rounded.Tv,
        dismissEnabled = !isBusy,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(text = stringResource(R.string.btn_cancel), onClick = onDismiss, enabled = !isBusy, modifier = Modifier.weight(1f))
            CassyPrimaryButton(
                text = stringResource(R.string.display_btn_create),
                onClick = onCreate,
                enabled = name.isNotBlank() && !isBusy,
                loading = isBusy,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        CassyTextField(
            value = name,
            onValueChange = onName,
            label = stringResource(R.string.display_field_name),
            placeholder = stringResource(R.string.display_field_name_hint),
            enabled = !isBusy
        )
        error?.let { CassyErrorBanner(message = it, tone = CassyToastTone.ERROR) }
    }
}

/** Shows the new screen's link once, with share and copy; the server cannot show it again. */
@Composable
fun CreatedDisplayDialog(created: CreatedDisplayScreen, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Without APP_PUBLIC_URL on the server there is no link; the TV then takes the bare code.
    val shareText = created.displayUrl.ifBlank { created.token }
    val shareTitle = stringResource(R.string.display_share_title, created.screen.name)
    CassyModalDialog(
        title = stringResource(R.string.display_created_title, created.screen.name),
        subtitle = stringResource(R.string.display_created_body),
        icon = Icons.Rounded.Tv,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(
                text = stringResource(R.string.display_btn_copy),
                onClick = { clipboard.setText(AnnotatedString(shareText)) },
                icon = Icons.Rounded.ContentCopy,
                modifier = Modifier.weight(1f)
            )
            CassyPrimaryButton(
                text = stringResource(R.string.display_btn_share),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
                icon = Icons.Rounded.Share,
                modifier = Modifier.weight(1f)
            )
        }
    ) {
        Text(
            text = stringResource(if (created.displayUrl.isBlank()) R.string.display_code_label else R.string.display_link_label),
            style = MaterialTheme.typography.labelLarge
        )
        SelectionContainer {
            Text(text = shareText, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        CassyErrorBanner(message = stringResource(R.string.display_once_warning), tone = CassyToastTone.WARNING)
    }
}

@Composable
fun RevokeDisplayDialog(screen: DisplayScreen, isBusy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CassyModalDialog(
        title = stringResource(R.string.display_revoke_title, screen.name),
        subtitle = stringResource(R.string.display_revoke_body),
        icon = Icons.Rounded.TvOff,
        dismissEnabled = !isBusy,
        onDismissRequest = onDismiss,
        footer = {
            CassySecondaryButton(text = stringResource(R.string.btn_cancel), onClick = onDismiss, enabled = !isBusy, modifier = Modifier.weight(1f))
            CassyDangerButton(
                text = stringResource(R.string.display_btn_revoke),
                onClick = onConfirm,
                enabled = !isBusy,
                loading = isBusy,
                modifier = Modifier.weight(1f)
            )
        }
    ) {}
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM HH:mm")

private fun dateOf(rfc3339: String): String =
    runCatching { OffsetDateTime.parse(rfc3339).atZoneSameInstant(ZoneId.systemDefault()).format(dateFormat) }.getOrDefault(rfc3339)
