package com.azuredevid.cassy.ui.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text a ViewModel hands to the UI without holding a Context: either a string resource (so ID/EN
 * follows the device locale) or a message the server already wrote for the user.
 */
sealed interface UiText {
    data class Raw(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Raw -> value
        is Res -> stringResource(id, *args.toTypedArray())
    }

    fun asString(context: Context): String = when (this) {
        is Raw -> value
        is Res -> context.getString(id, *args.toTypedArray())
    }

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

/** The server message when it has one, otherwise the local fallback resource. */
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.res(fallback)
