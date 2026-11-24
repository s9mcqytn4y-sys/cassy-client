package com.azuredevid.cassy.ui.pos

import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderItem
import com.azuredevid.cassy.domain.repository.ClosedTab
import com.azuredevid.cassy.domain.repository.ShareLine
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.domain.repository.TableTabRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Coordinates open table tabs, kitchen round submissions, tab closing/reopening, and equal/item splits.
 */
internal class PosTabCoordinator(
    private val tableTabRepository: TableTabRepository?
) {

    companion object {
        const val MAX_TAB_SPLITS = 20
    }

    fun applyTabOrder(stateFlow: MutableStateFlow<PosUiState>, tab: PosOrder) {
        stateFlow.update { state ->
            val products = state.allProducts.associateBy { it.id }
            val sent = tab.items.groupBy { it.productId }.mapNotNull { (productId, lines) ->
                products[productId]?.let {
                    CartItem(
                        product = it,
                        quantity = lines.sumOf { l -> l.quantity },
                        isSentToKitchen = true,
                        sentLines = lines
                    )
                }
            }
            state.copy(activeTabId = tab.id, cart = sent + state.cart.filterNot { it.isSentToKitchen })
        }
    }

    suspend fun submitTabRound(stateFlow: MutableStateFlow<PosUiState>, snapshot: PosUiState): Result<String> {
        val repo = tableTabRepository ?: return Result.failure(IllegalStateException("table tabs unavailable"))
        val tableId = snapshot.tableId ?: return Result.failure(IllegalStateException("no table selected"))
        val tabId = snapshot.activeTabId ?: run {
            val key = snapshot.pendingTabKey ?: UUID.randomUUID().toString()
            stateFlow.update { it.copy(pendingTabKey = key) }
            val tab = repo.openTab(key, tableId, snapshot.tableName).getOrElse { return Result.failure(it) }
            stateFlow.update { it.copy(activeTabId = tab.id, pendingTabKey = null) }
            tab.id
        }
        val unsent = snapshot.cart.filterNot { it.isSentToKitchen }
        if (unsent.isEmpty()) return Result.success(tabId)
        val roundKey = snapshot.pendingRoundKey ?: UUID.randomUUID().toString()
        stateFlow.update { it.copy(pendingRoundKey = roundKey) }
        repo.submitRound(
            orderId = tabId,
            idempotencyKey = roundKey,
            items = unsent.map { CreatePosOrderItemInput(it.product.id, it.quantity, it.notes) }
        ).getOrElse { return Result.failure(it) }
        stateFlow.update { state ->
            state.copy(
                pendingRoundKey = null,
                cart = state.cart.map { it.copy(isSentToKitchen = true) }
            )
        }
        repo.getTab(tabId).onSuccess { applyTabOrder(stateFlow, it) }
        return Result.success(tabId)
    }

    suspend fun closeTab(
        stateFlow: MutableStateFlow<PosUiState>,
        snapshot: PosUiState,
        close: suspend (repo: TableTabRepository, tabId: String) -> Result<ClosedTab>
    ): Result<PosOrder> {
        val repo = tableTabRepository ?: return Result.failure(IllegalStateException("table tabs unavailable"))
        val tabId = submitTabRound(stateFlow, snapshot).getOrElse { return Result.failure(it) }
        val closed = close(repo, tabId).getOrElse { return Result.failure(it) }
        stateFlow.update { state ->
            state.copy(
                pendingOrder = closed.order,
                pendingSplitId = closed.splits.firstOrNull { !it.isPaid }?.id,
                tabSplits = closed.splits.withLineNames(state.cart.flatMap { it.sentLines }),
                cart = state.cart.map { it.copy(sentLines = emptyList()) }
            )
        }
        return Result.success(closed.order)
    }

    /**
     * The cart still holds the sent lines here; the closed order may come back without items. A by_item
     * share gets its lines named; an equal share gets the whole bill so its receipt lists what was eaten.
     */
    private fun List<TabSplit>.withLineNames(sent: List<PosOrderItem>): List<TabSplit> {
        val names = sent.associate { it.id to it.snapshotName }
        val bill = sent.map { ShareLine(it.id, it.quantity, it.subtotalAmount, it.snapshotName) }
        return map { s ->
            if (s.items.isEmpty()) s.copy(billLines = bill)
            else s.copy(items = s.items.map { it.copy(name = names[it.orderItemId].orEmpty()) })
        }
    }

    suspend fun reopenTab(stateFlow: MutableStateFlow<PosUiState>, tabId: String): Result<PosOrder> {
        val repo = tableTabRepository ?: return Result.failure(IllegalStateException("table tabs unavailable"))
        val reopened = repo.reopenTab(tabId).getOrElse { return Result.failure(it) }
        stateFlow.update {
            it.copy(
                isSubmittingOrder = false,
                pendingOrder = null,
                pendingSplitId = null,
                tabSplits = emptyList(),
                tabSplitCount = 1
            )
        }
        repo.getTab(reopened.id).onSuccess { applyTabOrder(stateFlow, it) }
        return Result.success(reopened)
    }

    fun buildItemSplitDraft(state: PosUiState): ItemSplitDraft {
        val units = state.cart.flatMap { row ->
            row.sentLines.flatMap { line -> List(line.quantity) { SplitUnit(line.id, row.product.name) } }
        }
        return ItemSplitDraft(units)
    }

    fun setItemSplitGuests(draft: ItemSplitDraft, count: Int): ItemSplitDraft {
        val guests = count.coerceIn(2, minOf(MAX_TAB_SPLITS, draft.units.size))
        return draft.copy(guests = guests, owners = draft.owners.map { owner -> owner?.takeIf { it <= guests } })
    }

    fun assignUnit(draft: ItemSplitDraft, index: Int, guest: Int): ItemSplitDraft {
        return if (index !in draft.owners.indices || guest !in 1..draft.guests) {
            draft
        } else {
            draft.copy(owners = draft.owners.toMutableList().also { it[index] = guest })
        }
    }

    fun buildGuestItemShares(draft: ItemSplitDraft): List<Map<String, Int>> {
        return (1..draft.guests).map { guest ->
            draft.units.filterIndexed { i, _ -> draft.owners[i] == guest }.groupingBy { it.lineId }.eachCount()
        }
    }
}
