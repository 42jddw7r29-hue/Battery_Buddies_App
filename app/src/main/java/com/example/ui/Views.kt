@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessageEntity
import com.example.data.ChatRoomEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryBuddiesApp(viewModel: BatteryBuddiesViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val specsState by viewModel.specsState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CyberDarkBg, Color(0xFF0B132B))
                    )
                )
        ) {
            // Elegant Frosted Glass Background Drawing (Top glow radiating downwards)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Radial aura matching circle_at_50%_-20%, #3b82f644
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3B3B82F6), Color.Transparent),
                        center = Offset(size.width / 2f, -size.height * 0.15f),
                        radius = size.height * 0.65f
                    ),
                    radius = size.height * 0.65f,
                    center = Offset(size.width / 2f, -size.height * 0.15f)
                )

                // Additional subtle ambient pink glow at bottom right for gorgeous contrast
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x0CDB2777), Color.Transparent),
                        center = Offset(size.width * 1.0f, size.height * 0.9f),
                        radius = size.height * 0.45f
                    ),
                    radius = size.height * 0.45f,
                    center = Offset(size.width * 1.0f, size.height * 0.9f)
                )

                // Floating cyber network points or stars
                val seed = 42
                val random = java.util.Random(seed.toLong())
                for (i in 0..45) {
                    val x = random.nextFloat() * size.width
                    val y = random.nextFloat() * size.height
                    val radius = random.nextFloat() * 2f + 0.6f
                    val alpha = random.nextFloat() * 0.35f + 0.15f
                    drawCircle(
                        color = Color(0xFF60A5FA).copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y)
                    )
                }
            }

            AnimatedVisibility(
                visible = currentScreen is ScreenType.Welcome,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(400))
            ) {
                WelcomeView(
                    specs = specsState,
                    onStartScan = { viewModel.startHardwareScan() }
                )
            }

            AnimatedVisibility(
                visible = currentScreen is ScreenType.Scanning,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                val progress by viewModel.scanProgress.collectAsState()
                val detailsText by viewModel.scanDetailsText.collectAsState()
                ScanningView(progress = progress, detailsText = detailsText)
            }

            AnimatedVisibility(
                visible = currentScreen is ScreenType.Diagnostics,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(350))
            ) {
                val touchGridState by viewModel.touchGridState.collectAsState()
                DiagnosticsView(
                    specs = specsState,
                    touchGridState = touchGridState,
                    onCellTouch = { idx -> viewModel.toggleGridCell(idx) },
                    onSetCrackedMode = { enabled -> viewModel.setScreenCrackedMode(enabled) },
                    onResetGrid = { viewModel.resetTouchGrid() },
                    onComplete = { viewModel.skipOrCompleteDiagnostics() }
                )
            }

            AnimatedVisibility(
                visible = currentScreen is ScreenType.Dashboard,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(400))
            ) {
                val rooms by viewModel.matchedRooms.collectAsState()
                DashboardView(
                    specs = specsState,
                    rooms = rooms,
                    onSelectRoom = { room -> viewModel.navigateTo(ScreenType.ChatRoom(room)) },
                    onReScan = { viewModel.startHardwareScan() }
                )
            }

            if (currentScreen is ScreenType.ChatRoom) {
                val chatRoom = (currentScreen as ScreenType.ChatRoom).room
                val messages by viewModel.activeRoomMessages.collectAsState()
                val isSending by viewModel.isSendingMessage.collectAsState()
                ChatRoomView(
                    room = chatRoom,
                    messages = messages,
                    isSending = isSending,
                    onSendMessage = { text ->
                        viewModel.sendMessage(
                            chatRoom.roomId,
                            chatRoom.roomType,
                            chatRoom.matchValue,
                            text
                        )
                    },
                    onBack = { viewModel.navigateTo(ScreenType.Dashboard) },
                    onLeave = { viewModel.leaveCurrentRoom(chatRoom.roomId) }
                )
            }
        }
    }
}

// ============================================
// 1. WELCOME SCREEN WITH BRAIN CPU EMBLEM
// ============================================
@Composable
fun WelcomeView(
    specs: DeviceSpecs,
    onStartScan: () -> Unit
) {
    var showCreditsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. App Header (Frosted glass theme row)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Glass charging icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(CyberBlue, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Battery Buddies",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp,
                        modifier = Modifier.testTag("app_title")
                    )
                    Text(
                        text = "SECRET HARDWARE CHAT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Glass-frosted action/settings indicator button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x0EFFFFFF), CircleShape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CircleShape)
                    .clickable { showCreditsDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Credits",
                    tint = GlassTextLightSlate,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Center Glassmorphic Indicator with Pulse & Badge
        Box(
            modifier = Modifier
                .size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")
            val pulse1 by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = EaseInOutBack),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ring_1"
            )
            val pulse2 by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = EaseInOutBack),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ring_2"
            )

            // Outer pulse circle lines (Frosted glass rings)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.12f * pulse1)), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.22f * pulse2)), CircleShape)
            )

            // Inner highly-reflective Glass Container
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .background(Color(0x0EFFFFFF), CircleShape)
                    .border(BorderStroke(1.2.dp, Color.White.copy(alpha = 0.25f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${specs.batteryPct}%",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberCyan,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "البطارية الحالية",
                        fontSize = 11.sp,
                        color = GlassTextSlate,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // "MATCH FOUND" / "تم كشف المطابقة" badge positioned beautifully
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-5).dp)
                    .background(CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(CyberGreen, CircleShape)
                    )
                    Text(
                        text = "MATCH FOUND",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                }
            }
        }

        // Subtext explaining connection state
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "جاري تجميع الأجهزة المتطابقة...",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassTextLightSlate,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "فقط من يمتلكون نسبة بطارية ${specs.batteryPct}% يمكنهم دخول الغرفة السرية حالياً.",
                fontSize = 12.sp,
                color = GlassTextSlate,
                textAlign = TextAlign.Center
            )
        }

        // 3. User Controls & Custom Glass Footer Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Elegant solid white primary button with click scaling effect
            Button(
                onClick = onStartScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF020617)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("scan_button"),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Enter Room",
                        tint = Color(0xFF020617),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "افحص عتاد الجهاز الآن لجلب المطابقة",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Premium custom-styled Developer Footer (acting as credit trigger)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x08FFFFFF), RoundedCornerShape(20.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
                    .clickable { showCreditsDialog = true }
                    .padding(14.dp)
                    .testTag("credits_card")
            ) {
                Column {
                    Text(
                        text = "حقوق البرمجة والتطوير",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassTextSlate,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "امين محمد حسين",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .background(CyberBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .border(BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f)), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Developer",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.AlternateEmail, contentDescription = null, tint = CyberPurple, modifier = Modifier.size(14.dp))
                            Text(text = "@89oc9", fontSize = 10.sp, color = GlassTextSlate)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                            Text(text = "@ameen_alshammary", fontSize = 10.sp, color = GlassTextSlate)
                        }
                    }
                }
            }
        }
    }

    if (showCreditsDialog) {
        DeveloperCreditsDialog(onDismiss = { showCreditsDialog = false })
    }
}

// ============================================
// DEVELOPER CREDITS DIALOG
// ============================================
@Composable
fun DeveloperCreditsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "حقوق البرمجة والتطوير",
                color = CyberCyan,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "تم كتابتها وصنعها بكل فخر وشغف:",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "أمين محمد حسين",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Social items
                CreditsMediaButton(
                    label = "انستغرام (@89oc9)",
                    iconColor = Color(0xFFE1306C),
                    onClick = { uriHandler.openUri("https://instagram.com/89oc9") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                CreditsMediaButton(
                    label = "تيليغرام (@ameen_alshammary)",
                    iconColor = Color(0xFF0088cc),
                    onClick = { uriHandler.openUri("https://t.me/ameen_alshammary") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                CreditsMediaButton(
                    label = "واتساب للاتصال والتجارة",
                    iconColor = CyberGreen,
                    onClick = { uriHandler.openUri("https://wa.me/9647756786034") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "رقم الواتساب: 07756786034",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberDarkBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("إغلاق")
            }
        },
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp
    )
}

@Composable
fun CreditsMediaButton(
    label: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(iconColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================
// 2. SCANNING ANIMATED SCREEN
// ============================================
@Composable
fun ScanningView(
    progress: Float,
    detailsText: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_laser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = -1.02f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(Color(0x0EFFFFFF), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.2.dp, GlassBorderStrong), RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Scanner hologram lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepY = size.height * (laserOffset + 1f) / 2f
                // Laser line with gradient
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, CyberCyan, Color.Transparent),
                        startY = stepY - 12.dp.toPx(),
                        endY = stepY + 12.dp.toPx()
                    ),
                    start = Offset(0f, stepY),
                    end = Offset(size.width, stepY),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Processor Scanning",
                    tint = CyberCyan,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CyberCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Text statuses console-like
        Text(
            text = "جارِ تشخيص مكونات العتاد الحقيقي...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = detailsText,
            fontSize = 14.sp,
            color = CyberPurple,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = CyberCyan,
            trackColor = CyberSurfaceVariant
        )
    }
}

// ============================================
// 3. DIAGNOSTICS SCREEN (TOUCH GRID AND SENSORS)
// ============================================
@Composable
fun DiagnosticsView(
    specs: DeviceSpecs,
    touchGridState: List<Boolean>,
    onCellTouch: (Int) -> Unit,
    onSetCrackedMode: (Boolean) -> Unit,
    onResetGrid: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Core Header & Instruction
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "اختبار مصفوفة التاتش واللمس",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "اسحب إصبعك على المربعات لتأكيد اللمس وفحص الشروخ الحقيقية",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Toggles & Controls for Screen Break Simulation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "محاكاة خرق التاتش/شاشة مكسورة",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "يفعل مناطق معطلة بالفحص لكشف كسر افتراضي",
                    fontSize = 11.sp,
                    color = CyberPurple
                )
            }
            Switch(
                checked = specs.screenCrackedMode,
                onCheckedChange = { onSetCrackedMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberCyan,
                    checkedTrackColor = CyberPurple.copy(alpha = 0.4f)
                )
            )
        }

        // 6x6 Touch Grid Calibration View
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color(0x0EFFFFFF), RoundedCornerShape(16.dp))
                .border(BorderStroke(1.2.dp, GlassBorder), RoundedCornerShape(16.dp))
                .pointerInput(specs.screenCrackedMode) {
                    // Track dragged touches across cells
                    detectDragGestures { change, _ ->
                        val cellWidth = size.width / 6
                        val cellHeight = size.height / 6
                        val x = change.position.x
                        val y = change.position.y
                        if (x in 0f..size.width.toFloat() && y in 0f..size.height.toFloat()) {
                            val col = (x / cellWidth).toInt().coerceIn(0, 5)
                            val row = (y / cellHeight).toInt().coerceIn(0, 5)
                            val idx = row * 6 + col
                            onCellTouch(idx)
                        }
                    }
                }
                .pointerInput(specs.screenCrackedMode) {
                    // Track single taps
                    detectTapGestures { offset ->
                        val cellWidth = size.width / 6
                        val cellHeight = size.height / 6
                        val col = (offset.x / cellWidth).toInt().coerceIn(0, 5)
                        val row = (offset.y / cellHeight).toInt().coerceIn(0, 5)
                        val idx = row * 6 + col
                        onCellTouch(idx)
                    }
                }
        ) {
            // Screen Grid drawing
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(6) { row ->
                    Row(modifier = Modifier.weight(1f)) {
                        repeat(6) { col ->
                            val index = row * 6 + col
                            val isActive = touchGridState[index]

                            // Simulating dead zones
                            val isSimulatedDead = specs.screenCrackedMode && (index == 14 || index == 21 || index == 22)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, Color.White.copy(alpha = 0.08f))
                                    .background(
                                        when {
                                            isSimulatedDead -> CyberRed.copy(alpha = 0.7f)
                                            isActive -> CyberGreen.copy(alpha = 0.4f)
                                            else -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSimulatedDead) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dead Zone",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Proximity Sensor real-time diagnostic bar (frosted card style)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "مستشعر القرب والخصوصية:",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (specs.proximityDist < 1.0f) "مغطى (Covered - Privacy ON)" else "مفتوح (Open)",
                        fontSize = 12.sp,
                        color = if (specs.proximityDist < 1.0f) CyberGreen else CyberOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (specs.proximityDist < 1.0f) 1.0f else 0.1f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = if (specs.proximityDist < 1.0f) CyberGreen else CyberOrange,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }

        // Actions (Premium themed buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onResetGrid,
                border = BorderStroke(1.dp, GlassBorderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("تصفير الفحص", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onComplete,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("إكمال التشخيص", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================
// 4. DASHBOARD VIEW (AVAILABLE HARWARE CHATS)
// ============================================
@Composable
fun DashboardView(
    specs: DeviceSpecs,
    rooms: List<ChatRoomEntity>,
    onSelectRoom: (ChatRoomEntity) -> Unit,
    onReScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Top Header Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x13FFFFFF)),
            border = BorderStroke(1.dp, GlassBorderStrong),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "بصمة جهازك الرقمية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                    IconButton(onClick = onReScan) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "ReScan", tint = CyberCyan)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SpecsCompactItem(
                        imageVector = Icons.Default.BatteryChargingFull,
                        label = "نسبة البطارية",
                        value = "${specs.batteryPct}%",
                        color = if (specs.batteryPct > 30) CyberGreen else CyberRed
                    )
                    SpecsCompactItem(
                        imageVector = Icons.Default.DeveloperBoard,
                        label = "المعالج",
                        value = specs.cpuBoard,
                        color = CyberPurple
                    )
                    SpecsCompactItem(
                        imageVector = Icons.Default.Screenshot,
                        label = "تقييم الشاشة",
                        value = if (specs.screenCrackedMode) "Grade B" else "Grade A",
                        color = if (specs.screenCrackedMode) CyberOrange else CyberCyan
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(CyberCyan, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "غرف عتاد سرية مطابقة لجهازك الآن:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Rooms Column list matching specs exactly!
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rooms) { room ->
                Card(
                    onClick = { onSelectRoom(room) },
                    colors = CardDefaults.cardColors(containerColor = Color(0x0CFFFFFF)),
                    border = BorderStroke(1.dp, GlassBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Emblem representing Room type (with frosted design)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = when(room.roomType) {
                                    "BATTERY" -> Icons.Default.Battery0Bar
                                    "PROCESSOR" -> Icons.Default.Memory
                                    else -> Icons.Default.TouchApp
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))

                            Column {
                                Text(
                                    text = room.roomNameAr,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = room.roomNameEn,
                                    fontSize = 12.sp,
                                    color = GlassTextSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Connected Tag pill (high contrast white)
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "دخول",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF020617)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Info notice alert
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x06FFFFFF)),
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = "Security", tint = CyberCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "جميع المحادثات مشفرة بالكامل بالنظير للنظير ومخبأة في ذاكرة الهاتف لضمان سريتك المطلقة.",
                    fontSize = 11.sp,
                    color = GlassTextSlate,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun SpecsCompactItem(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ============================================
// 5. CHAT ROOM SCREEN (MOCK AND GEMINI POWERED)
// ============================================
@Composable
fun ChatRoomView(
    room: ChatRoomEntity,
    messages: List<ChatMessageEntity>,
    isSending: Boolean,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    onLeave: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = room.roomNameAr,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Text(
                            text = "أصحاب مواصفات: ${room.matchValue}",
                            fontSize = 11.sp,
                            color = GlassTextSlate
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onLeave) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Leave", tint = CyberRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x08FFFFFF)
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color(0x13FFFFFF),
                border = BorderStroke(1.dp, GlassBorder),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("أكتب سراً للمطابقين...", fontSize = 14.sp, color = GlassTextSlate) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .testTag("chat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    onSendMessage(textInput)
                                    textInput = ""
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank() && !isSending) {
                                onSendMessage(textInput)
                                textInput = ""
                            }
                        },
                        containerColor = Color.White,
                        contentColor = Color(0xFF020617),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("send_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF020617), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    val alignment = if (message.isUser) Alignment.End else Alignment.Start
                    val containerColor = if (message.isUser) Color.White else Color(0x0CFFFFFF)
                    val textColor = if (message.isUser) Color(0xFF020617) else Color.White
                    val borderStroke = if (message.isUser) null else BorderStroke(1.dp, GlassBorder)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = alignment
                    ) {
                        // Sender Nickname Tag
                        if (!message.isUser) {
                            Text(
                                text = message.senderName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberCyan,
                                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                            )
                        }

                        // Message main bubble
                        Surface(
                            shape = if (message.isUser) {
                                RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
                            } else {
                                RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
                            },
                            color = containerColor,
                            border = borderStroke,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = message.messageText,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
