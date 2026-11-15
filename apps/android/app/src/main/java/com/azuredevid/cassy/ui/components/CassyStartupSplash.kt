package com.azuredevid.cassy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.theme.CassyPandan
import com.azuredevid.cassy.ui.theme.CassyPutihTulang
import com.azuredevid.cassy.ui.theme.CassyTinta
import com.azuredevid.cassy.ui.theme.LightSecondaryInk

/**
 * In-app bootstrap splash rendered only while SessionManager finishes Tink decryption
 * after Android 12+ native splash handoff. Zero artificial delay.
 */
@Composable
fun CassyStartupSplash(
    modifier: Modifier = Modifier,
    subtitle: String = stringResource(R.string.splash_loading_subtitle)
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CassyPutihTulang)
            .padding(CassyDimens.SpaceXl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = CassyDimens.ElevationSubtle
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_cassy_monogram),
                    contentDescription = stringResource(R.string.a11y_cassy_logo),
                    modifier = Modifier
                        .padding(CassyDimens.SpaceXl)
                        .size(84.dp)
                )
            }
            Spacer(Modifier.height(CassyDimens.SpaceXl))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = CassyTinta
            )
            Spacer(Modifier.height(CassyDimens.SpaceXs))
            Text(
                text = stringResource(R.string.pairing_brand_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = LightSecondaryInk
            )
            Spacer(Modifier.height(CassyDimens.SpaceXl))
            LinearProgressIndicator(
                color = CassyPandan,
                trackColor = CassyPandan.copy(alpha = 0.18f),
                modifier = Modifier
                    .width(180.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
            Spacer(Modifier.height(CassyDimens.SpaceMd))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LightSecondaryInk
            )
        }
    }
}
