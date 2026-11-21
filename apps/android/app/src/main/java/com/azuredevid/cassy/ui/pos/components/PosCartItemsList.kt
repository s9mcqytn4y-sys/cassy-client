package com.azuredevid.cassy.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azuredevid.cassy.R
import com.azuredevid.cassy.ui.designsystem.CassyCard
import com.azuredevid.cassy.ui.designsystem.CassyDimens
import com.azuredevid.cassy.ui.designsystem.CassyEmptyState
import com.azuredevid.cassy.ui.pos.CartItem
import java.text.NumberFormat

@Composable
internal fun PosCartItemsList(
    cart: List<CartItem>,
    isCartLocked: Boolean,
    rupiahFormat: NumberFormat,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (cart.isEmpty()) {
            CassyEmptyState(
                icon = Icons.Rounded.ShoppingBag,
                title = stringResource(R.string.cart_empty_title),
                subtitle = stringResource(R.string.cart_empty_subtitle),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CassyDimens.SpaceSm)
            ) {
                items(cart, key = { "${it.product.id}-${it.isSentToKitchen}" }) { item ->
                    val decreaseDesc = stringResource(R.string.a11y_decrease_qty, item.product.name)
                    val increaseDesc = stringResource(R.string.a11y_increase_qty, item.product.name)
                    val lineTotal = item.product.basePriceIdr * item.quantity

                    CassyCard(contentPadding = CassyDimens.SpaceMd) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = CassyDimens.SpaceSm)
                            ) {
                                Text(
                                    text = item.product.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.cash_chip_rp,
                                        rupiahFormat.format(lineTotal)
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.isSentToKitchen) {
                                    Text(
                                        text = stringResource(R.string.cart_line_sent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CassyDimens.SpaceXs)
                            ) {
                                OutlinedButton(
                                    onClick = { onDecreaseQuantity(item) },
                                    // A sent line can only be taken back as a void of a server tab line.
                                    enabled = !isCartLocked && (!item.isSentToKitchen || item.sentLines.isNotEmpty()),
                                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(CassyDimens.TouchTargetMin)
                                        .semantics { contentDescription = decreaseDesc }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "${item.quantity}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.widthIn(min = 28.dp),
                                    textAlign = TextAlign.Center
                                )

                                OutlinedButton(
                                    onClick = { onIncreaseQuantity(item) },
                                    enabled = !isCartLocked,
                                    shape = RoundedCornerShape(CassyDimens.RadiusButton),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .size(CassyDimens.TouchTargetMin)
                                        .semantics { contentDescription = increaseDesc }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
