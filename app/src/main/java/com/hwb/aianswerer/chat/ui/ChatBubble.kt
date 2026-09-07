package com.hwb.aianswerer.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.chat.model.MessageRole

@Composable
internal fun chatBackground(): Color = if (MaterialTheme.colorScheme.background.luminance() > .5f)
    Color(0xFFEDEDED) else Color(0xFF191919)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ChatBubble(content: String, role: MessageRole, name: String, onClick: () -> Unit = {},
                        onLongClick: () -> Unit = onClick) {
    val self = role == MessageRole.SELF
    val unknown = role == MessageRole.UNKNOWN
    if (role == MessageRole.SYSTEM) {
        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(content, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top) {
        if (!self) { ChatAvatar(name); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 280.dp).weight(1f, fill = false)) {
            if (unknown) Text("身份待确认 · 点按修正", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
            Surface(
                color = if (self) Color(0xFF95EC69) else MaterialTheme.colorScheme.surface,
                contentColor = if (self) Color(0xFF162410) else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = if (self) 8.dp else 2.dp, topEnd = if (self) 2.dp else 8.dp,
                    bottomStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) { Text(content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge) }
        }
        if (self) { Spacer(Modifier.width(8.dp)); ChatAvatar("我", true) }
    }
}

@Composable
internal fun ChatAvatar(name: String, self: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = if (self) Color(0xFF647C71) else Color(0xFF687DA2)) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Text(name.take(1).ifBlank { "友" }, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}
