package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.GeminiFlashClient
import com.example.data.QuizPreset
import com.example.data.QuizPresets
import com.example.data.SolveResponse
import com.example.service.FloatingSolverService
import com.example.ui.theme.AmberFlash
import com.example.ui.theme.CrimsonStandby
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.FlashSolveTheme
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashSolveTheme {
                FlashSolveDashboard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashSolveDashboard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permission state check
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = checkOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Service state
    val isOverlayRunning by FloatingSolverService.isRunning.collectAsState()
    val isSolverActive by FloatingSolverService.isSolverActive.collectAsState()
    val serviceLastResult by FloatingSolverService.latestResult.collectAsState()

    // Test Sandbox state
    var selectedPreset by remember { mutableStateOf<QuizPreset?>(QuizPresets.items.first()) }
    var currentPrompt by remember { mutableStateOf(QuizPresets.items.first().questionPrompt) }
    var customApiKeyInput by remember { mutableStateOf("") }
    var isSolving by remember { mutableStateOf(false) }
    var localSolveResult by remember { mutableStateOf<SolveResponse?>(null) }
    var showMobileGuide by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPreset) {
        selectedPreset?.let {
            currentPrompt = it.questionPrompt
            FloatingSolverService.activeQuestionPrompt = it.questionPrompt
        }
    }

    Scaffold(
        containerColor = HudBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(EmeraldActive),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "FlashSolve Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FLASHSOLVE AI",
                                color = TextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Ultra-Fast Quiz & Math HUD • Gemini Flash",
                                color = EmeraldActive,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMobileGuide = !showMobileGuide },
                        modifier = Modifier.testTag("guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Mobile Run Guide",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HudSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. OVERLAY PERMISSION BANNER
            if (!hasOverlayPermission) {
                item {
                    OverlayPermissionWarningCard(
                        onGrantClicked = {
                            requestOverlayPermission(context)
                        }
                    )
                }
            }

            // 2. MASTER FLOATING OVERLAY CONTROL CARD
            item {
                FloatingServiceControlCard(
                    isServiceRunning = isOverlayRunning,
                    isSolverActive = isSolverActive,
                    hasPermission = hasOverlayPermission,
                    onToggleService = {
                        if (!hasOverlayPermission) {
                            requestOverlayPermission(context)
                        } else {
                            if (isOverlayRunning) {
                                context.stopService(Intent(context, FloatingSolverService::class.java))
                            } else {
                                FloatingSolverService.customApiKey = customApiKeyInput.ifBlank { null }
                                val intent = Intent(context, FloatingSolverService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        }
                    }
                )
            }

            // 3. MOBILE WORKFLOW GUIDE (Expandable)
            if (showMobileGuide) {
                item {
                    MobileDevGuideCard(onDismiss = { showMobileGuide = false })
                }
            }

            // 4. IN-APP AI QUIZ & MATH SANDBOX
            item {
                Text(
                    text = "LIVE SOLVER SANDBOX",
                    color = EmeraldActive,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }

            // Preset selector chips (Arabic Science, English Algebra, History, Geometry, Biology)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(QuizPresets.items) { preset ->
                        val isSelected = selectedPreset?.id == preset.id
                        val chipColor by animateColorAsState(
                            if (isSelected) EmeraldActive else HudSurfaceVariant,
                            label = "chipColor"
                        )
                        val textColor = if (isSelected) Color.Black else TextPrimary

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = chipColor,
                            modifier = Modifier
                                .clickable {
                                    selectedPreset = preset
                                }
                                .border(1.dp, if (isSelected) EmeraldActive else HudBorder, RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.title,
                                    color = textColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${preset.language})",
                                    color = if (isSelected) Color(0xFF00381B) else TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Question Input Box
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, HudBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Question / مسألة الاختبار",
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = selectedPreset?.subject ?: "Custom",
                                color = CyanAccent,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = currentPrompt,
                            onValueChange = {
                                currentPrompt = it
                                FloatingSolverService.activeQuestionPrompt = it
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .testTag("question_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HudSurfaceVariant,
                                unfocusedContainerColor = HudSurfaceVariant,
                                focusedBorderColor = EmeraldActive,
                                unfocusedBorderColor = HudBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom API Key optional input
                        OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = {
                                customApiKeyInput = it
                                FloatingSolverService.customApiKey = it.ifBlank { null }
                            },
                            label = { Text("Gemini API Key (Optional override / اختياري)", color = TextSecondary, fontSize = 11.sp) },
                            placeholder = { Text("Uses injected Secrets key by default", color = TextMuted, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HudBackground,
                                unfocusedContainerColor = HudBackground,
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = HudBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                isSolving = true
                                coroutineScope.launch {
                                    val result = GeminiFlashClient.solveText(
                                        prompt = currentPrompt,
                                        customApiKey = customApiKeyInput.ifBlank { null }
                                    )
                                    localSolveResult = result
                                    isSolving = false
                                }
                            },
                            enabled = !isSolving && currentPrompt.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("solve_sandbox_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldActive,
                                disabledContainerColor = HudSurfaceVariant
                            )
                        ) {
                            if (isSolving) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SOLVING WITH GEMINI FLASH...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SOLVE QUESTION NOW (حل الآن)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // 5. SOLVE RESULT CARD (Instant HUD Format)
            val displayResult = localSolveResult ?: serviceLastResult
            if (displayResult != null) {
                item {
                    InstantResultCard(result = displayResult)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun OverlayPermissionWarningCard(onGrantClicked: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CrimsonStandby, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonStandby)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Overlay Permission Required",
                    color = CrimsonStandby,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "FlashSolve needs 'Display over other apps' to float over your quiz sessions and provide sub-1.5s answers in real time.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantClicked,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonStandby),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grant_permission_button")
            ) {
                Text("Grant Overlay Permission", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FloatingServiceControlCard(
    isServiceRunning: Boolean,
    isSolverActive: Boolean,
    hasPermission: Boolean,
    onToggleService: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isServiceRunning) EmeraldActive else HudBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLOATING OVERLAY CONTROLLER",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isServiceRunning) "Running over all apps" else "Service stopped",
                        color = if (isServiceRunning) EmeraldActive else TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isServiceRunning) Color(0xFF00381B) else HudSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isServiceRunning) EmeraldActive else HudBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!isServiceRunning) CrimsonStandby
                                    else if (isSolverActive) EmeraldActive
                                    else AmberFlash
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!isServiceRunning) "Inactive"
                            else if (isSolverActive) "Active (جاهز)"
                            else "Standby (متوقف)",
                            color = if (isServiceRunning) EmeraldActive else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button
            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) CrimsonStandby else EmeraldActive
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("toggle_overlay_service_button")
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (isServiceRunning) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "Stop Floating Overlay (إيقاف النافذة)"
                    else "Launch Floating HUD (تشغيل النافذة العائمة)",
                    color = if (isServiceRunning) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun InstantResultCard(result: SolveResponse) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (result.isSuccess) AmberFlash else CrimsonStandby, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INSTANT FLASH RESULT",
                    color = AmberFlash,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )

                // Latency Badge
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldActive)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = EmeraldActive, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${result.latencyMs} ms",
                            color = EmeraldActive,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big Answer Callout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (result.isSuccess) Color(0xFF2E2207) else Color(0xFF3B1515))
                        .border(1.5.dp, if (result.isSuccess) AmberFlash else CrimsonStandby, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = result.answer,
                        color = if (result.isSuccess) AmberFlash else CrimsonStandby,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (result.isSuccess) "Correct Option / الحل الصحيح" else "Error Occurred",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Confidence: ${result.confidence}",
                        color = CyanAccent,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Concise explanation
            Text(
                text = result.explanation,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HudSurfaceVariant, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            )
        }
    }
}

@Composable
fun MobileDevGuideCard(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B2E)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyanAccent, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mobile Phone Run & Test Guide",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "✕",
                    color = TextSecondary,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val steps = listOf(
                "1. Direct Browser Test: You can test the app right now in the Streaming Android Emulator directly from your phone browser without installing anything.",
                "2. Export APK: Tap Project Settings in the top bar → Download APK directly to your phone storage.",
                "3. Grant Overlay: On your phone, go to Settings → Apps → FlashSolve → Display over other apps → Toggle ON.",
                "4. Start Overlay: Open FlashSolve, tap 'Launch Floating HUD', switch to any quiz (Kahoot, Telegram, Quizizz), and tap 'SOLVE' for sub-1.5s answers."
            )

            steps.forEach { step ->
                Text(
                    text = step,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}

fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val genericIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
        }
    }
}
