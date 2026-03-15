/*
 * Copyright (C) 2026 Asanoha Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */
package com.banka.directlens

import android.app.role.RoleManager
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

fun isDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        return roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    }
    val setting = Settings.Secure.getString(context.contentResolver, "assistant") ?: return false
    return setting.contains(context.packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("directlens_prefs", Context.MODE_PRIVATE)
    val configManager = remember { OverlayConfigurationManager(context) }
    
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isAssistantSet by remember { mutableStateOf(isDefaultAssistant(context)) }
    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("master_enabled", true)) }
    var hapticStrength by remember { mutableIntStateOf(prefs.getInt("haptic_strength", 50)) }
    var rainbowFlashEnabled by remember { mutableStateOf(prefs.getBoolean("rainbow_flash_enabled", true)) }
    var config by remember { mutableStateOf(configManager.getConfig()) }
    var showWelcomeDialog by remember { mutableStateOf(prefs.getBoolean("first_launch", true)) }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val screenWidth = context.resources.displayMetrics.widthPixels
    val screenHeight = context.resources.displayMetrics.heightPixels

    // Detection du mode de navigation
    var isGestureMode by remember { 
        mutableStateOf(Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2)
    }

    var showAccessibilityPopup by remember { mutableStateOf(false) }
    var showAssistantPopup by remember { mutableStateOf(false) }

    fun updateConfig(newConfig: OverlayConfig) {
        config = newConfig
        configManager.saveConfig(newConfig)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                isAssistantSet = isDefaultAssistant(context)
                
                val currentMode = Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2
                if (currentMode != isGestureMode) {
                    isGestureMode = currentMode
                    
                    // ON LIT LA CONFIG FRAICHE POUR ÉVITER LES BUGS DE FERMETURE (STALE CLOSURE)
                    val freshConfig = configManager.getConfig()
                    if (freshConfig.segments.size >= 2) {
                        val newSegments = freshConfig.segments.toMutableList()
                        // Zone 1 forcee selon mode
                        newSegments[0] = newSegments[0].copy(isEnabled = isGestureMode)
                        // Zone 2 forcee selon mode
                        newSegments[1] = newSegments[1].copy(isEnabled = !isGestureMode)
                        
                        val finalConfig = freshConfig.copy(segments = newSegments)
                        updateConfig(finalConfig)
                        // Rafraîchir l'état local de l'UI
                        config = finalConfig
                    }
                }
                
                if (!showWelcomeDialog && isAccessibilityEnabled && !isGestureMode && !isAssistantSet && !prefs.getBoolean("assistant_popup_dismissed", false)) {
                    showAssistantPopup = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(showWelcomeDialog, isAccessibilityEnabled) {
        if (!showWelcomeDialog && !isAccessibilityEnabled) {
            showAccessibilityPopup = true
        } else if (!showWelcomeDialog && isAccessibilityEnabled && !isGestureMode && !isAssistantSet && !prefs.getBoolean("assistant_popup_dismissed", false)) {
            showAssistantPopup = true
        }
    }

    if (showAccessibilityPopup) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    showAccessibilityPopup = false
                }) { Text("Open Settings") }
            },
            title = { Text("Permission Required", textAlign = TextAlign.Center) },
            text = { Text("DirectLens requires Accessibility Service to capture screenshots and launch Google Lens. Please enable it first.", textAlign = TextAlign.Center) },
            icon = { Icon(Icons.Default.AccessibilityNew, null, tint = MaterialTheme.colorScheme.error) },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showAssistantPopup) {
        AlertDialog(
            onDismissRequest = { 
                showAssistantPopup = false
                prefs.edit().putBoolean("assistant_popup_dismissed", true).apply()
            },
            confirmButton = {
                Button(onClick = {
                    if (config.segments.size >= 2) {
                        val newSegments = config.segments.toMutableList()
                        newSegments[0] = newSegments[0].copy(isEnabled = false)
                        newSegments[1] = newSegments[1].copy(isEnabled = true)
                        updateConfig(config.copy(segments = newSegments))
                    }
                    try {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                    showAssistantPopup = false
                    prefs.edit().putBoolean("assistant_popup_dismissed", true).apply()
                }) { Text(stringResource(R.string.assistant_button)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (config.segments.size >= 2) {
                        val newSegments = config.segments.toMutableList()
                        newSegments[0] = newSegments[0].copy(isEnabled = false)
                        newSegments[1] = newSegments[1].copy(isEnabled = true)
                        updateConfig(config.copy(segments = newSegments))
                    }
                    showAssistantPopup = false
                    prefs.edit().putBoolean("assistant_popup_dismissed", true).apply()
                }) { Text("Maybe later") }
            },
            title = { Text(stringResource(R.string.assistant_card_title), textAlign = TextAlign.Center) },
            text = { Text(stringResource(R.string.assistant_card_desc), textAlign = TextAlign.Center) },
            shape = RoundedCornerShape(28.dp)
        )
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
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.disclosure_text), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/banka89"))) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text(stringResource(R.string.donate_button))
                    }
                }
            },
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
                        rainbowFlashEnabled = true
                        masterEnabled = true
                        prefs.edit().putInt("haptic_strength", 50).putBoolean("rainbow_flash_enabled", true).putBoolean("master_enabled", true).apply()
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Reset") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.welcome_text).substringBefore("\n\n"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/banka89"))) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(stringResource(R.string.donate_button), maxLines = 1)
                }
                
                Button(
                    onClick = { 
                        val packageName = context.packageName
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                        } catch (e: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.rate_button), maxLines = 1)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

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
            
            Spacer(modifier = Modifier.height(12.dp))

            // SECTION ASSISTANT
            SettingsSectionHeader(title = stringResource(R.string.assistant_header))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (!isGestureMode && !isAssistantSet) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isAssistantSet) Icons.Default.CheckCircle else Icons.Default.Android, null, tint = if (isAssistantSet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.assistant_card_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = if (isAssistantSet) "DirectLens is currently your default assistant. Long-press Home to trigger it." else stringResource(R.string.assistant_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { 
                            try {
                                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = if (isAssistantSet) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (isAssistantSet) "Change Assistant" else stringResource(R.string.assistant_button))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            if (!isAccessibilityEnabled) {
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
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.rate_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.rate_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = {
                            val packageName = context.packageName
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                            } catch (e: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, null)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Visual Effects
            SettingsSectionHeader(title = stringResource(R.string.visual_header))
            SettingsToggleItem(
                title = stringResource(R.string.rainbow_flash),
                subtitle = stringResource(R.string.rainbow_flash_desc),
                icon = Icons.Default.Palette,
                checked = rainbowFlashEnabled,
                onCheckedChange = {
                    rainbowFlashEnabled = it
                    prefs.edit().putBoolean("rainbow_flash_enabled", it).apply()
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Haptique HD
            if (isGestureMode) {
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
            }

            // 4. Zones de détection
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zones_header).uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = MaterialTheme.colorScheme.primary)
                Switch(checked = config.isVisible, onCheckedChange = { updateConfig(config.copy(isVisible = it)) }, thumbContent = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(12.dp)) })
            }
            config.segments.forEachIndexed { index, segment ->
                SegmentEditorItem(
                    index = index, 
                    segment = segment, 
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    isExpanded = config.activeSegmentIndex == index, 
                    onExpandToggle = { updateConfig(config.copy(activeSegmentIndex = if (config.activeSegmentIndex == index) -1 else index)) }, 
                    onUpdate = { updated -> val newSegments = config.segments.toMutableList(); newSegments[index] = updated; updateConfig(config.copy(segments = newSegments)) }, 
                    onDelete = { val newSegments = config.segments.toMutableList(); newSegments.removeAt(index); updateConfig(config.copy(segments = newSegments, activeSegmentIndex = -1)) }
                )
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

            // 5. SECTION INFORMATIONS
            SettingsSectionHeader(title = stringResource(R.string.about_header))
            
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
fun SegmentEditorItem(index: Int, segment: OverlaySegment, screenWidth: Int, screenHeight: Int, isExpanded: Boolean, onExpandToggle: () -> Unit, onUpdate: (OverlaySegment) -> Unit, onDelete: () -> Unit) {
    val alpha = if (segment.isEnabled) 1f else 0.5f
    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (index == 0) Icons.Default.Gesture 
                                 else if (index == 1) Icons.Default.Square 
                                 else Icons.Default.Rectangle,
                    contentDescription = null,
                    tint = if (segment.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (index == 0) stringResource(R.string.zone_gesture)
                           else if (index == 1) stringResource(R.string.zone_corner)
                           else stringResource(R.string.zone_label, index + 1),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = segment.isEnabled, onCheckedChange = { onUpdate(segment.copy(isEnabled = it)) }, modifier = Modifier.scale(0.8f))
                
                if (index > 1) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }

                IconButton(onClick = onExpandToggle) { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SliderWithLabel(stringResource(R.string.dim_width), segment.width, 100, screenWidth) { onUpdate(segment.copy(width = it)) }
                    SliderWithLabel(stringResource(R.string.dim_height), segment.height, 50, 600) { onUpdate(segment.copy(height = it)) }
                    SliderWithLabel(stringResource(R.string.pos_x), segment.xOffset, 0, screenWidth) { onUpdate(segment.copy(xOffset = it)) }
                    SliderWithLabel(stringResource(R.string.pos_y), segment.yOffset, 0, screenHeight) { onUpdate(segment.copy(yOffset = it)) }
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
