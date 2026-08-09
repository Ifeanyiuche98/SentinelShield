package com.sentinelshield.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sentinelshield.services.antitheft.SentinelDeviceAdmin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiTheftScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("antitheft_prefs", Context.MODE_PRIVATE)

    var isEnabled by remember { mutableStateOf(prefs.getBoolean("antitheft_enabled", false)) }
    var securityPin by remember { mutableStateOf(prefs.getString("security_pin", "") ?: "") }
    var trustedNumber by remember { mutableStateOf(prefs.getString("trusted_number", "") ?: "") }
    var simDetection by remember { mutableStateOf(prefs.getBoolean("sim_detection_enabled", false)) }
    var maxFailedAttempts by remember { mutableStateOf(prefs.getInt("max_failed_attempts", 5).toString()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var isDeviceAdminActive by remember { mutableStateOf(false) }

    // Check if device admin is active
    LaunchedEffect(Unit) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, SentinelDeviceAdmin::class.java)
        isDeviceAdminActive = dpm.isAdminActive(adminComponent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Anti-Theft Protection",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Protect your device from theft with remote commands",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Device Admin Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDeviceAdminActive)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDeviceAdminActive) Icons.Default.VerifiedUser else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isDeviceAdminActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDeviceAdminActive) "Device Admin Active" else "Device Admin Required",
                        fontWeight = FontWeight.Bold,
                        color = if (isDeviceAdminActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = if (isDeviceAdminActive)
                            "Remote lock and wipe are available"
                        else
                            "Enable for remote lock/wipe features",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDeviceAdminActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (!isDeviceAdminActive) {
                    Button(
                        onClick = {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(
                                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                    ComponentName(context, SentinelDeviceAdmin::class.java)
                                )
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "SentinelShield needs Device Admin to enable remote lock and wipe."
                                )
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Enable")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Master Toggle
        SettingToggleCard(
            icon = Icons.Default.Shield,
            title = "Anti-Theft Protection",
            subtitle = if (isEnabled) "Active - device is protected" else "Disabled",
            isChecked = isEnabled,
            onCheckedChange = { enabled ->
                if (enabled && securityPin.isEmpty()) {
                    showPinDialog = true
                } else {
                    isEnabled = enabled
                    prefs.edit().putBoolean("antitheft_enabled", enabled).apply()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Security PIN
        OutlinedTextField(
            value = securityPin,
            onValueChange = { newPin ->
                if (newPin.length <= 8 && newPin.all { it.isDigit() }) {
                    securityPin = newPin
                    prefs.edit().putString("security_pin", newPin).apply()
                }
            },
            label = { Text("Security PIN (4-8 digits)") },
            placeholder = { Text("Enter your secret PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Trusted Number
        OutlinedTextField(
            value = trustedNumber,
            onValueChange = { number ->
                trustedNumber = number
                prefs.edit().putString("trusted_number", number).apply()
            },
            label = { Text("Trusted Phone Number") },
            placeholder = { Text("e.g., +2348012345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // SIM Change Detection
        SettingToggleCard(
            icon = Icons.Default.SimCard,
            title = "SIM Change Detection",
            subtitle = "Alert if SIM card is swapped",
            isChecked = simDetection,
            onCheckedChange = { enabled ->
                simDetection = enabled
                prefs.edit().putBoolean("sim_detection_enabled", enabled).apply()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Max Failed Attempts
        OutlinedTextField(
            value = maxFailedAttempts,
            onValueChange = { value ->
                if (value.length <= 2 && value.all { it.isDigit() }) {
                    maxFailedAttempts = value
                    val attempts = value.toIntOrNull() ?: 5
                    prefs.edit().putInt("max_failed_attempts", attempts).apply()
                }
            },
            label = { Text("Max Failed Unlock Attempts") },
            placeholder = { Text("5") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Photo captured after this many failed attempts") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Commands Reference Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SMS Commands",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Send these commands via SMS to your device:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                CommandItem("SENTINEL_LOCK_<PIN>", "Lock device")
                CommandItem("SENTINEL_LOCATE_<PIN>", "Get GPS location")
                CommandItem("SENTINEL_ALARM_<PIN>", "Trigger loud alarm")
                CommandItem("SENTINEL_PHOTO_<PIN>", "Capture intruder photo")
                CommandItem("SENTINEL_WIPE_<PIN>", "Factory reset (⚠️ DANGER)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // PIN Setup Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set Security PIN") },
            text = { Text("You need to set a security PIN before enabling anti-theft protection. This PIN will be used in SMS commands.") },
            confirmButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SettingToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun CommandItem(command: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "• ",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Column {
            Text(
                text = command,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
