package com.azuredevid.cassy.ui.kds.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.azuredevid.cassy.R
import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.ui.designsystem.CassyErrorBanner
import com.azuredevid.cassy.ui.designsystem.CassyToastTone

/**
 * Persistent banner while live updates are not flowing (never a toast: the kitchen must keep seeing it).
 * Hidden when connected. [onRefresh] reloads the board over REST in the meantime.
 */
@Composable
fun KdsConnectionBanner(
    state: RealtimeConnectionState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messageRes = when (state) {
        RealtimeConnectionState.CONNECTED -> return
        RealtimeConnectionState.CONNECTING -> R.string.kds_conn_connecting
        RealtimeConnectionState.RECONNECTING -> R.string.kds_conn_reconnecting
        RealtimeConnectionState.DISCONNECTED -> R.string.kds_conn_disconnected
    }
    CassyErrorBanner(
        message = stringResource(messageRes),
        tone = if (state == RealtimeConnectionState.CONNECTING) CassyToastTone.INFO else CassyToastTone.WARNING,
        onRetry = onRefresh,
        modifier = modifier.fillMaxWidth()
    )
}
