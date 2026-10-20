package com.examcamp.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.examcamp.app.R
import com.examcamp.app.SyncStatus
import com.examcamp.app.auth.AuthState
import com.examcamp.app.auth.AuthUser
import com.examcamp.app.i18n.Lang
import com.examcamp.app.i18n.t
import com.examcamp.app.ui.theme.Ec
import com.examcamp.app.ui.theme.EcIcons
import com.examcamp.app.ui.theme.Radius
import com.examcamp.app.ui.theme.Space

/** .site-header: bg bar with one hairline, brand on the left, actions on the right (64dp on phones). */
@Composable
fun SiteHeader(
    dark: Boolean,
    lang: Lang,
    auth: AuthState,
    sync: SyncStatus,
    onToggleTheme: () -> Unit,
    onLangChange: (Lang) -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {},
) {
    val c = Ec.c
    val widthDp = LocalConfiguration.current.screenWidthDp
    val compact = widthDp <= 640
    val showLang = widthDp > 480
    val iconSize = if (compact) 36.dp else 44.dp

    Column(Modifier.fillMaxWidth().background(c.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 64.dp else 72.dp)
                .padding(horizontal = if (compact) Space.s4 else Space.s6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) Space.s1 else Space.s3)) {
                Image(
                    painter = painterResource(R.drawable.brand_icon),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = t("brand.name"),
                    style = (if (compact) Ec.t.bodyStrong else Ec.t.h3).copy(letterSpacing = (-0.02).em),
                    color = c.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                EcIconButton(
                    icon = if (dark) EcIcons.Moon else EcIcons.Sun,
                    description = "Toggle theme",
                    onClick = onToggleTheme,
                    size = iconSize,
                )
                if (showLang) {
                    EcSegmented(
                        options = listOf("en" to "EN", "bn" to "বাংলা"),
                        selected = lang.code,
                        onSelect = { onLangChange(Lang.from(it)) },
                        compact = compact,
                    )
                }
                EcIconButton(icon = EcIcons.Gear, description = t("settings.title"), onClick = onOpenSettings, size = iconSize)
                AuthWidget(auth, sync, compact, onSignIn, onSignOut, onDeleteAccount)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun AuthWidget(
    auth: AuthState,
    sync: SyncStatus,
    compact: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = Ec.c
    when (auth) {
        is AuthState.SignedIn -> UserMenu(auth.user, sync, onSignOut, onDeleteAccount)
        else -> {
            val shape = RoundedCornerShape(Radius.sm)
            if (compact) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(shape)
                        .border(1.dp, c.textPrimary, shape)
                        .clickable(role = Role.Button, onClick = onSignIn),
                    contentAlignment = Alignment.Center,
                ) { EcIcon(EcIcons.Google, Color.Unspecified, 18.dp) }
            } else {
                EcButton(
                    text = t("auth.signIn"),
                    onClick = onSignIn,
                    style = BtnStyle.Secondary,
                    small = true,
                    leading = { EcIcon(EcIcons.Google, Color.Unspecified, 18.dp) },
                )
            }
        }
    }
}

@Composable
private fun syncColor(sync: SyncStatus): Color = when (sync) {
    SyncStatus.SYNCED -> Ec.c.success
    SyncStatus.SYNCING -> Ec.c.warning
    SyncStatus.ERROR -> Ec.c.danger
    SyncStatus.OFFLINE -> Ec.c.textMuted
}

@Composable
private fun syncLabel(sync: SyncStatus): String = when (sync) {
    SyncStatus.SYNCED -> t("auth.synced")
    SyncStatus.SYNCING -> "Syncing…"
    SyncStatus.ERROR -> "Sync failed - changes saved locally"
    SyncStatus.OFFLINE -> t("net.offlineShort")
}

@Composable
private fun Avatar(user: AuthUser, size: androidx.compose.ui.unit.Dp) {
    val c = Ec.c
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(c.surfaceAlt).border(2.dp, c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = (user.name ?: user.email ?: "?").trim().take(1).uppercase()
        Text(initial, style = Ec.t.smallStrong, color = c.textSecondary)
        if (user.photoUrl != null) {
            AsyncImage(
                model = user.photoUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun UserMenu(user: AuthUser, sync: SyncStatus, onSignOut: () -> Unit, onDeleteAccount: () -> Unit) {
    val c = Ec.c
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box {
        Box(Modifier.size(36.dp).clickable(role = Role.Button) { open = !open }) {
            Avatar(user, 36.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(syncColor(sync))
                    .border(2.dp, c.surface, CircleShape),
            )
        }
        if (open) {
            val shape = RoundedCornerShape(Radius.md)
            Popup(
                alignment = Alignment.TopEnd,
                // 24dp of padding around the card gives its blurred shadow room inside the popup window.
                offset = IntOffset(with(density) { 24.dp.roundToPx() }, with(density) { 22.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .width(260.dp)
                        // .auth-widget__menu: 0 10px 30px rgba(0,0,0,.12)
                        .boxShadow(Color(0x1F000000), 10.dp, 30.dp, Radius.md)
                        .clip(shape)
                        .background(c.surface)
                        .border(1.dp, c.border, shape)
                        .padding(Space.s3),
                    verticalArrangement = Arrangement.spacedBy(Space.s3),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                        Avatar(user, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(user.name ?: "", style = Ec.t.bodyStrong, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(user.email ?: "", style = Ec.t.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(syncColor(sync)))
                        Text(syncLabel(sync), style = Ec.t.small, color = c.textSecondary)
                    }
                    EcButton(
                        text = t("auth.signOut"),
                        onClick = { open = false; onSignOut() },
                        modifier = Modifier.fillMaxWidth(),
                        style = BtnStyle.Secondary,
                        small = true,
                    )
                    Text(
                        text = t("auth.deleteAccount"),
                        style = Ec.t.small,
                        color = c.danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(role = Role.Button) { open = false; confirmDelete = true }
                            .padding(horizontal = Space.s2, vertical = Space.s2),
                    )
                }
            }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(t("auth.deleteModal.title"), style = Ec.t.h4, color = c.textPrimary) },
                text = { Text(t("auth.deleteModal.body"), style = Ec.t.body, color = c.textPrimary) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; onDeleteAccount() }) {
                        Text(t("auth.deleteModal.confirm"), color = c.danger)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(t("common.cancel"), color = c.textSecondary)
                    }
                },
                shape = RoundedCornerShape(Radius.lg),
                containerColor = c.surface,
                tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
            )
        }
    }
}
