package com.pixelexperience.forall.lockscreen.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.components.GlassCard
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockFont
import java.util.Date

private class Group(val id: String, val appName: String, val items: List<LockNotification>)

@Composable
fun NotificationsList(
    items: List<LockNotification>,
    grouped: Boolean,
    opacity: Float,
    tint: Color,
    accent: Color,
    preview: Boolean,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(items, grouped) {
        if (grouped) items.groupBy { it.packageName }.map { (pkg, l) -> Group(pkg, l.first().appName, l) }
        else items.map { Group(it.key, it.appName, listOf(it)) }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = !preview,
    ) {
        items(groups, key = { it.id }) { g ->
            GroupCard(g, opacity, tint, accent, onOpen, onDismiss, Modifier.animateItem())
        }
        if (items.any { it.clearable }) {
            item(key = "clear_all") {
                Box(Modifier.fillMaxWidth().animateItem(), contentAlignment = Alignment.CenterEnd) {
                    GlassCard(
                        modifier = Modifier.clickable { onClearAll() },
                        shape = RoundedCornerShape(50),
                        tint = tint,
                        opacity = opacity,
                    ) {
                        Text(
                            "Limpar tudo",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    g: Group,
    opacity: Float,
    tint: Color,
    accent: Color,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }
    val multi = g.items.size > 1
    val first = g.items.first()
    val canExpand = multi || first.bigText.length > first.text.length + 3
    val shape = RoundedCornerShape(32.dp)
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, LockMotion.spatial(), label = "arrow")
    val stacked = multi && !expanded

    Box(modifier) {
        if (stacked) {
            GlassCard(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp).fillMaxWidth().height(60.dp),
                shape, tint, opacity * 0.7f,
            ) {}
        }
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (stacked) 9.dp else 0.dp)
                .animateContentSize(LockMotion.spatial()),
            shape = shape, tint = tint, opacity = opacity,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(first, 34.dp, accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        g.appName, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                        fontFamily = lockFont(600), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (multi) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.clip(CircleShape).background(accent.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("${g.items.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        DateFormat.getTimeFormat(ctx).format(Date(first.postTime)),
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    )
                    if (canExpand) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown, contentDescription = if (expanded) "Recolher" else "Expandir",
                            tint = Color.White,
                            modifier = Modifier.padding(start = 6.dp).size(26.dp).rotate(arrow)
                                .clip(CircleShape).clickable { expanded = !expanded },
                        )
                    }
                }
                g.items.forEachIndexed { i, n ->
                    AnimatedVisibility(
                        visible = i == 0 || expanded,
                        enter = expandVertically(LockMotion.spatial()) + fadeIn(LockMotion.effects()),
                        exit = shrinkVertically(LockMotion.spatial()) + fadeOut(LockMotion.effects()),
                    ) {
                        NotificationRow(n, expanded, onOpen, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    n: LockNotification,
    expanded: Boolean,
    onOpen: (LockNotification) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled && n.clearable) { onDismiss(n.key); true } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        enableDismissFromStartToEnd = n.clearable,
        enableDismissFromEndToStart = n.clearable,
    ) {
        Column(Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(top = 8.dp)) {
            if (n.title.isNotBlank()) {
                Text(
                    n.title, color = Color.White, fontSize = 15.sp, fontFamily = lockFont(600),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val body = if (expanded && n.bigText.isNotBlank()) n.bigText else n.text
            if (body.isNotBlank()) {
                Text(
                    body, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp,
                    maxLines = if (expanded) 6 else 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(n: LockNotification, size: Dp, accent: Color) {
    val bg = if (n.color != 0) Color(n.color or 0xFF000000.toInt()) else accent.copy(alpha = 0.6f)
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val icon = n.icon
        if (icon != null) {
            Image(icon, null, Modifier.size(size * 0.58f), colorFilter = ColorFilter.tint(Color.White))
        } else {
            Text(n.appName.take(1).uppercase(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
