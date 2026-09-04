@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hwb.aianswerer.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.chat.storage.ConversationAIConfigEntity

@Composable
fun ConversationConfigScreen(initial: ConversationAIConfigEntity, onSave: (ConversationAIConfigEntity) -> Unit, onBack: () -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    val background = MaterialTheme.colorScheme.background
    Scaffold(
        containerColor = background,
        topBar = { TopAppBar(
            title = { Text("联系人个性化设置") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
        ) }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("每一项都可以使用系统配置，也可以只为这个联系人单独设置。",
                    modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            ConfigField("API Base URL", value.inheritApiBaseUrl, value.apiBaseUrl, { value = value.copy(inheritApiBaseUrl = it) }, { value = value.copy(apiBaseUrl = it) })
            ConfigField("API Key", value.inheritApiKey, value.apiKey, { value = value.copy(inheritApiKey = it) }, { value = value.copy(apiKey = it) }, password = true)
            ConfigField("Model", value.inheritModel, value.model, { value = value.copy(inheritModel = it) }, { value = value.copy(model = it) })
            ConfigField("System Prompt", value.inheritSystemPrompt, value.systemPrompt, { value = value.copy(inheritSystemPrompt = it) }, { value = value.copy(systemPrompt = it) }, singleLine = false)
            ConfigField("Temperature", value.inheritTemperature, value.temperature.toString(), { value = value.copy(inheritTemperature = it) }, { it.toDoubleOrNull()?.let { n -> value = value.copy(temperature = n.coerceIn(0.0, 2.0)) } })
            ConfigField("Max Tokens", value.inheritMaxTokens, value.maxTokens.toString(), { value = value.copy(inheritMaxTokens = it) }, { it.toIntOrNull()?.let { n -> value = value.copy(maxTokens = n.coerceIn(64, 32768)) } })
            Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text("保存设置") }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    inherit: Boolean,
    value: String,
    onInheritChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    singleLine: Boolean = true
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("使用系统配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Switch(checked = inherit, onCheckedChange = onInheritChange)
            }
            if (!inherit) OutlinedTextField(
                value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine, minLines = if (singleLine) 1 else 3,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
            )
        }
    }
}
