package com.sentinelshield.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinelshield.data.models.AppPermissionInfo
import com.sentinelshield.viewmodels.PermissionAuditorViewModel

@Composable
fun PermissionAuditorScreen(viewModel: PermissionAuditorViewModel = viewModel()) {
    val appPermissions by viewModel.appPermissions.collectAsState()
    val isAuditing by viewModel.isAuditing.collectAsState()
    val overlayApps by viewModel.overlayApps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Permission Auditor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.auditAllApps() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isAuditing,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAuditing) "Auditing..." else "Audit All Apps")
        }

        if (isAuditing) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay warning
        if (overlayApps.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${overlayApps.size} app(s) can draw overlays (clickjacking risk)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // App list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(appPermissions) { app ->
                PermissionCard(app)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCard(app: AppPermissionInfo) {
    val riskColor = when {
        app.riskScore >= 70 -> Color(0xFFF44336)
        app.riskScore >= 50 -> Color(0xFFFF9800)
        app.riskScore >= 30 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Risk score indicator
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = app.riskScore / 100f,
                        modifier = Modifier.size(40.dp),
                        color = riskColor,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "${app.riskScore}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${app.dangerousPermissions.size} dangerous permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.dangerousPermissions.size > 3) riskColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                if (app.dangerousPermissions.isNotEmpty()) {
                    Text(
                        text = "Dangerous Permissions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = riskColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    app.dangerousPermissions.forEach { perm ->
                        Text(
                            text = "• $perm",
                            style = MaterialTheme.typography.bodySmall,
                            color = riskColor
                        )
                    }
                }

                if (app.normalPermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Normal Permissions: ${app.normalPermissions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
