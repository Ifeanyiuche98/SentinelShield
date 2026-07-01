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
import com.sentinelshield.data.models.RiskLevel
import com.sentinelshield.data.models.ScanResult
import com.sentinelshield.viewmodels.ScanResultsViewModel

@Composable
fun ScanResultsScreen(viewModel: ScanResultsViewModel = viewModel()) {
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Scan Results",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.scanAllApps() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "Scanning..." else "Start Full Scan")
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = scanProgress,
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Scanning apps... ${(scanProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scanResults.isNotEmpty()) {
            val threats = scanResults.count { it.riskLevel.ordinal >= 3 }
            val safe = scanResults.count { it.riskLevel == RiskLevel.SAFE }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(modifier = Modifier.weight(1f), label = "Total: ${scanResults.size}", color = MaterialTheme.colorScheme.surfaceVariant)
                SummaryChip(modifier = Modifier.weight(1f), label = "Safe: $safe", color = Color(0xFF1B5E20).copy(alpha = 0.2f))
                SummaryChip(modifier = Modifier.weight(1f), label = "Threats: $threats", color = Color(0xFFB71C1C).copy(alpha = 0.2f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(scanResults) { result ->
                ScanResultCard(result)
            }
        }
    }
}

@Composable
fun ScanResultCard(result: ScanResult) {
    val riskColor = when (result.riskLevel) {
        RiskLevel.SAFE -> Color(0xFF4CAF50)
        RiskLevel.LOW -> Color(0xFF8BC34A)
        RiskLevel.MEDIUM -> Color(0xFFFF9800)
        RiskLevel.HIGH -> Color(0xFFFF5722)
        RiskLevel.CRITICAL -> Color(0xFFF44336)
    }

    val riskIcon = when (result.riskLevel) {
        RiskLevel.SAFE -> Icons.Filled.CheckCircle
        RiskLevel.LOW -> Icons.Filled.Info
        RiskLevel.MEDIUM -> Icons.Filled.Warning
        RiskLevel.HIGH -> Icons.Filled.Error
        RiskLevel.CRITICAL -> Icons.Filled.Dangerous
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = riskIcon, contentDescription = null, tint = riskColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = result.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = result.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.threats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    result.threats.take(2).forEach { threat ->
                        Text(text = "• $threat", style = MaterialTheme.typography.bodySmall, color = riskColor)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(8.dp), color = riskColor.copy(alpha = 0.15f)) {
                Text(
                    text = result.riskLevel.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SummaryChip(modifier: Modifier = Modifier, label: String, color: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            text = label,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
