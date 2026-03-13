/*
 * Copyright (C) 2026 Asanoha Labs
 * DirectLens is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package com.banka.directlens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.banka.directlens.ui.theme.DirectLensTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DirectLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainSettingsScreen()
                }
            }
        }
    }
}

fun playHapticPreview(context: Context, level: Int) {
    if (level == 0) return
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val amplitude = (level * 2.55f).toInt().coerceIn(1, 255)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(40, amplitude))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(40)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("directlens_prefs", Context.MODE_PRIVATE)
    val configManager = remember { OverlayConfigurationManager(context) }
    
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("master_enabled", true)) }
    var hapticStrength by remember { mutableIntStateOf(prefs.getInt("haptic_strength", 50)) }
    var config by remember { mutableStateOf(configManager.getConfig()) }
    var showWelcomeDialog by remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    fun updateConfig(newConfig: OverlayConfig) {
        config = newConfig
        configManager.saveConfig(newConfig)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showWelcomeDialog) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(onClick = {
                    showWelcomeDialog = false
                    prefs.edit().putBoolean("first_launch", false).apply()
                }) { Text(stringResource(R.string.welcome_button)) }
            },
            icon = { Image(painterResource(R.drawable.favicon), null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) },
            title = { Text(stringResource(R.string.welcome_title), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.welcome_text), textAlign = TextAlign.Center) },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.favicon), null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        configManager.resetConfig()
                        updateConfig(configManager.getConfig())
                        hapticStrength = 50
                        prefs.edit().putInt("haptic_strength", 50).apply()
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Reset") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Image(painterResource(R.drawable.favicontitle), null, modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f), contentScale = ContentScale.Fit)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.welcome_text).substringBefore("\n\n"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))

            // 1. Service Switch
            SettingsToggleItem(
                title = stringResource(R.string.service_active),
                subtitle = if (masterEnabled) stringResource(R.string.service_active_sub) else stringResource(R.string.service_hidden_sub),
                icon = Icons.Default.PowerSettingsNew,
                checked = masterEnabled,
                onCheckedChange = {
                    masterEnabled = it
                    prefs.edit().putBoolean("master_enabled", it).apply()
                }
            )

            // Permission Warning
            if (!isAccessibilityEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Accessibility, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.service_disabled), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(stringResource(R.string.service_disabled_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Haptique HD
            SettingsSectionHeader(title = stringResource(R.string.haptic_header))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.haptic_strength), style = MaterialTheme.typography.titleSmall)
                        Text("$hapticStrength%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = hapticStrength.toFloat(), onValueChange = { hapticStrength = it.toInt(); prefs.edit().putInt("haptic_strength", it.toInt()).apply(); playHapticPreview(context, it.toInt()) }, valueRange = 1f..100f)
                    Text(stringResource(R.string.haptic_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Zones de détection
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zones_header).uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.primary)
                Switch(checked = config.isVisible, onCheckedChange = { updateConfig(config.copy(isVisible = it)) }, thumbContent = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(12.dp)) })
            }
            config.segments.forEachIndexed { index, segment ->
                SegmentEditorItem(index = index, segment = segment, isExpanded = config.activeSegmentIndex == index, onExpandToggle = { updateConfig(config.copy(activeSegmentIndex = if (config.activeSegmentIndex == index) -1 else index)) }, onUpdate = { updated -> val newSegments = config.segments.toMutableList(); newSegments[index] = updated; updateConfig(config.copy(segments = newSegments)) }, onDelete = { val newSegments = config.segments.toMutableList(); newSegments.removeAt(index); updateConfig(config.copy(segments = newSegments, activeSegmentIndex = -1)) })
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (masterEnabled) {
                Button(onClick = { val currentSegments = config.segments.toMutableList(); val metrics = context.resources.displayMetrics; currentSegments.add(OverlaySegment(yOffset = metrics.heightPixels - 200)); updateConfig(config.copy(segments = currentSegments)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_zone))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. SECTION INFORMATIONS (FAQ & GITHUB)
            SettingsSectionHeader(title = stringResource(R.string.about_header))
            
            // GitHub Button
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Bankairim/DirectLens/"))) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Code, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.github_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(title = stringResource(R.string.faq_battery_title), text = stringResource(R.string.faq_battery_text), icon = Icons.Default.BatteryChargingFull)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = stringResource(R.string.faq_privacy_title), text = stringResource(R.string.faq_privacy_text), icon = Icons.Default.Security)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = stringResource(R.string.faq_disclaimer_title), text = stringResource(R.string.faq_disclaimer_text), icon = Icons.Default.Gavel)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = stringResource(R.string.faq_opensource_title), text = stringResource(R.string.faq_opensource_text), icon = Icons.Default.Public)
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // FOOTER AVEC COPYRIGHT DYNAMIQUE
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.title), null, modifier = Modifier.height(20.dp).alpha(0.4f), contentScale = ContentScale.Fit)
                Text(
                    text = "© 2026 - $currentYear Asanoha Labs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun InfoCard(title: String, text: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
fun SettingsToggleItem(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SegmentEditorItem(index: Int, segment: OverlaySegment, isExpanded: Boolean, onExpandToggle: () -> Unit, onUpdate: (OverlaySegment) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onExpandToggle() }) {
                Icon(Icons.Default.Rectangle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.zone_label, index + 1), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SliderWithLabel(stringResource(R.string.dim_width), segment.width, 100, 1080) { onUpdate(segment.copy(width = it)) }
                    SliderWithLabel(stringResource(R.string.dim_height), segment.height, 50, 600) { onUpdate(segment.copy(height = it)) }
                    SliderWithLabel(stringResource(R.string.pos_x), segment.xOffset, 0, 1080) { onUpdate(segment.copy(xOffset = it)) }
                    SliderWithLabel(stringResource(R.string.pos_y), segment.yOffset, 0, 2600) { onUpdate(segment.copy(yOffset = it)) }
                }
            }
        }
    }
}

@Composable
fun SliderWithLabel(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $value", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value = value.toFloat(), onValueChange = { onValueChange(it.toInt()) }, valueRange = min.toFloat()..max.toFloat(), modifier = Modifier.weight(1f))
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, "com.banka.directlens.DirectLensService")
    val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) return true
    }
    return false
}
