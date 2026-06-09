package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simulation.*
import com.example.ui.theme.*
import kotlin.math.*
import kotlin.random.Random
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_scaffold")
                    ) { innerPadding ->
                        MainScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Struct for visual water bubbles
data class DecoBubble(
    var x: Float,
    var y: Float,
    val speed: Float,
    val radius: Float
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: SimulationViewModel = viewModel()
) {
    // Collect settings flows
    val preyBirthRate by viewModel.r.collectAsStateWithLifecycle()
    val huntMortalityRate by viewModel.a.collectAsStateWithLifecycle()
    val predDeathRate by viewModel.s.collectAsStateWithLifecycle()
    val predReproductionRate by viewModel.b.collectAsStateWithLifecycle()

    val initPreySeed by viewModel.initPrey.collectAsStateWithLifecycle()
    val initPredatorSeed by viewModel.initPredator.collectAsStateWithLifecycle()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val simSpeed by viewModel.simSpeed.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    // Collect data streams
    val fishList by viewModel.fishList.collectAsStateWithLifecycle()
    val sharkList by viewModel.sharkList.collectAsStateWithLifecycle()
    val odeHistory by viewModel.odeHistory.collectAsStateWithLifecycle()
    val ecoHistory by viewModel.ecoHistory.collectAsStateWithLifecycle()

    // Collect counters
    val fishBirthCount by viewModel.fishBirthCount.collectAsStateWithLifecycle()
    val fishDeathCount by viewModel.fishDeathCount.collectAsStateWithLifecycle()
    val sharkBirthCount by viewModel.sharkBirthCount.collectAsStateWithLifecycle()
    val sharkDeathCount by viewModel.sharkDeathCount.collectAsStateWithLifecycle()
    
    val peakPrey by viewModel.peakPrey.collectAsStateWithLifecycle()
    val peakPredator by viewModel.peakPredator.collectAsStateWithLifecycle()

    // Local dialog controller
    var showExplanationDialog by remember { mutableStateOf(false) }

    // Adaptive orientation check
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .background(OceanDarkBackground)
            .fillMaxSize()
    ) {
        if (isLandscape) {
            // Horizontal split for wide devices/landscape mode
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Simulation Graphic Canvas or Graph
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScreenHeader(onHelpClick = { showExplanationDialog = true })
                    ViewModeSelector(viewMode = viewMode, onModeChanged = { viewModel.setViewMode(it) })

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (viewMode == "Ecosystem") {
                            EcosystemPlayground(
                                fishList = fishList,
                                sharkList = sharkList,
                                onAddFish = { viewModel.addManualFish() },
                                onAddShark = { viewModel.addManualShark() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AnalyticalDetailsView(
                                odeHistory = odeHistory,
                                peakPrey = peakPrey,
                                peakPredator = peakPredator,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    PlayControlsBar(
                        isPlaying = isPlaying,
                        simSpeed = simSpeed,
                        onTogglePlay = { viewModel.togglePlay() },
                        onReset = { viewModel.resetSimulation() },
                        onSpeedChanged = { viewModel.setSpeed(it) }
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Right Panel: Sliders & Statistics
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ParametersSection(
                            r = preyBirthRate,
                            onRChange = { viewModel.setR(it) },
                            a = huntMortalityRate,
                            onAChange = { viewModel.setA(it) },
                            s = predDeathRate,
                            onSChange = { viewModel.setS(it) },
                            b = predReproductionRate,
                            onBChange = { viewModel.setB(it) }
                        )
                    }

                    item {
                        SeedsSection(
                            initPrey = initPreySeed,
                            onInitPreyChange = { viewModel.setInitPrey(it) },
                            initPredator = initPredatorSeed,
                            onInitPredatorChange = { viewModel.setInitPredator(it) }
                        )
                    }

                    item {
                        StatisticsSection(
                            fishCount = if (viewMode == "Ecosystem") fishList.size.toFloat() else odeHistory.lastOrNull()?.prey ?: 0f,
                            sharkCount = if (viewMode == "Ecosystem") sharkList.size.toFloat() else odeHistory.lastOrNull()?.predator ?: 0f,
                            fishBirth = fishBirthCount,
                            fishDeath = fishDeathCount,
                            sharkBirth = sharkBirthCount,
                            sharkDeath = sharkDeathCount,
                            peakP = peakPrey,
                            peakS = peakPredator,
                            isEcosystem = viewMode == "Ecosystem"
                        )
                    }
                }
            }
        } else {
            // Vertical Stack layout (Optimized for Portrait mobile screens)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScreenHeader(onHelpClick = { showExplanationDialog = true })
                
                ViewModeSelector(
                    viewMode = viewMode, 
                    onModeChanged = { viewModel.setViewMode(it) }
                )

                // The visual simulator takes proportional weight
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxWidth()
                ) {
                    if (viewMode == "Ecosystem") {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EcosystemPlayground(
                                fishList = fishList,
                                sharkList = sharkList,
                                onAddFish = { viewModel.addManualFish() },
                                onAddShark = { viewModel.addManualShark() },
                                modifier = Modifier.weight(1f)
                            )
                            // Smaller pop graph below canvas representing the actual spatial counts
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ThemeOutline),
                                modifier = Modifier.height(110.dp)
                            ) {
                                Box(modifier = Modifier.padding(6.dp)) {
                                    PopulationChart(
                                        history = ecoHistory,
                                        peakPrey = maxOf(10f, peakPrey),
                                        peakPredator = maxOf(10f, peakPredator),
                                        modifier = Modifier.fillMaxSize(),
                                        title = "روند تغییر جمعیت فیزیکی (Spatial Count Chart)"
                                    )
                                }
                            }
                        }
                    } else {
                        AnalyticalDetailsView(
                            odeHistory = odeHistory,
                            peakPrey = peakPrey,
                            peakPredator = peakPredator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                PlayControlsBar(
                    isPlaying = isPlaying,
                    simSpeed = simSpeed,
                    onTogglePlay = { viewModel.togglePlay() },
                    onReset = { viewModel.resetSimulation() },
                    onSpeedChanged = { viewModel.setSpeed(it) }
                )

                // Scrollable configs dashboard
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ParametersSection(
                            r = preyBirthRate,
                            onRChange = { viewModel.setR(it) },
                            a = huntMortalityRate,
                            onAChange = { viewModel.setA(it) },
                            s = predDeathRate,
                            onSChange = { viewModel.setS(it) },
                            b = predReproductionRate,
                            onBChange = { viewModel.setB(it) }
                        )
                    }

                    item {
                        SeedsSection(
                            initPrey = initPreySeed,
                            onInitPreyChange = { viewModel.setInitPrey(it) },
                            initPredator = initPredatorSeed,
                            onInitPredatorChange = { viewModel.setInitPredator(it) }
                        )
                    }

                    item {
                        StatisticsSection(
                            fishCount = if (viewMode == "Ecosystem") fishList.size.toFloat() else odeHistory.lastOrNull()?.prey ?: 0f,
                            sharkCount = if (viewMode == "Ecosystem") sharkList.size.toFloat() else odeHistory.lastOrNull()?.predator ?: 0f,
                            fishBirth = fishBirthCount,
                            fishDeath = fishDeathCount,
                            sharkBirth = sharkBirthCount,
                            sharkDeath = sharkDeathCount,
                            peakP = peakPrey,
                            peakS = peakPredator,
                            isEcosystem = viewMode == "Ecosystem"
                        )
                    }
                }
            }
        }
    }

    // Mathematical Explanation Dialog of Lotka-Volterra equations (academic-grade doc!)
    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = "Help Guide", tint = BrandPrimary) },
            title = {
                Text(
                    text = "مدل شبیه‌سازی صید و صیاد (Lotka-Volterra)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OceanOnSurface,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "این برنامه تعامل بین صید (ماهی🐟) و صیاد (کوسه🦈) را با دو رویکرد موازی پیاده‌سازی می‌کند:",
                        fontSize = 14.sp,
                        color = OceanOnSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = ThemeOutline)

                    Text(
                        text = "۱. شبیه‌سازی تحلیلی (سیستم ریاضی):",
                        fontWeight = FontWeight.Bold,
                        color = BrandPrimary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "معادلات دیفرانسیل زیر به روش رونگه-کوتا مرتبه چهارم (RK4) با دقت بسیار بالا حل شده و مسیر نوسانی پایداری در فضا-فاز ترسیم می‌شود:\n\n" +
                                "• نرخ تغییرات صید (ماهی‌ها):\n" +
                                "  dX/dt = r•X - a•X•Y\n" +
                                "• نرخ تغییرات صیاد (کوسه‌ها):\n" +
                                "  dY/dt = -s•Y + b•X•Y",
                        fontSize = 13.sp,
                        color = OceanOnSurface.copy(alpha = 0.80f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = ThemeOutline)

                    Text(
                        text = "۲. شبیه‌سازی بصری (بوم اکوسیستم ۲ بعدی):",
                        fontWeight = FontWeight.Bold,
                        color = OceanSecondary,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "یک اکوسیستم فیزیکی زنده و هوشمند! ماهی‌ها به صورت تصادفی شنا می‌کنند و با دیدن کوسه‌ها فرار می‌کنند و بر اساس ضریب r تولید مثل می‌کنند. کوسه‌ها بوی ماهی را تشخیص داده، آن‌ها را شکار کرده تا انرژی جذب کنند. اگر کوسه انرژی تمام کند از گرسنگی می‌میرد (بر اساس نرخ s) و اگر انرژی زیادی جذب کند تقسیم می‌شود (بر اساس ضریب b).",
                        fontSize = 13.sp,
                        color = OceanOnSurface.copy(alpha = 0.80f),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 راهنما: می‌توانید دکمه‌های [ریاضی / اکوسیستم] بالا را تغییر داده، متغیرها را با اسلایدرها بروز کنید و بر روی بوم فیزیکی کلیک کنید تا موجود جدید به صورت دستی تزریق شود!",
                            fontSize = 12.sp,
                            color = BrandOnContainer,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showExplanationDialog = false },
                    modifier = Modifier.testTag("help_ok_button")
                ) {
                    Text("متوجه شدم (Dismiss)", color = BrandPrimary)
                }
            },
            containerColor = OceanDarkSurface
        )
    }
}

// --- SUB LEVEL COMPONENTS ---

@Composable
fun ScreenHeader(
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "شبیه‌ساز پیوسته صید و صیاد (کوسه و ماهی)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = BrandPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
              )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Continuous & Spatial Simulation Project",
                    fontSize = 11.sp,
                    color = TextSubtitle,
                    textAlign = TextAlign.Right
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier
                .background(OceanDarkSurface, RoundedCornerShape(12.dp))
                .border(1.dp, ThemeOutline, RoundedCornerShape(12.dp))
                .testTag("help_icon_button")
        ) {
            Icon(Icons.Filled.Info, contentDescription = "Information Document", tint = BrandPrimary)
        }
    }
}

@Composable
fun ViewModeSelector(
    viewMode: String,
    onModeChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OceanDarkSurface)
            .border(1.dp, ThemeOutline, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val modes = listOf(
            Pair("Ecosystem", "اکوسیستم فیزیکی (گسسته)"),
            Pair("Analytical", "شبیه‌ساز ریاضی (پیوسته)")
        )

        modes.forEach { (modeKey, modeTitle) ->
            val isSelected = viewMode == modeKey
            val bgCol by animateColorAsState(if (isSelected) BrandPrimary else Color.Transparent, label = "bg")
            val textCol by animateColorAsState(if (isSelected) Color.White else OceanOnSurface.copy(alpha = 0.7f), label = "text")

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bgCol)
                    .clickable { onModeChanged(modeKey) }
                    .height(48.dp)
                    .testTag("tab_$modeKey"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = modeTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textCol
                )
            }
        }
    }
}

@Composable
fun PlayControlsBar(
    isPlaying: Boolean,
    simSpeed: Int,
    onTogglePlay: () -> Unit,
    onReset: () -> Unit,
    onSpeedChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OceanDarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThemeOutline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Controls: Speed multipliers
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1, 2, 4).forEach { speed ->
                    val isSelected = simSpeed == speed
                    Button(
                        onClick = { onSpeedChanged(speed) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) BrandPrimary else OceanOnSurface.copy(alpha = 0.05f),
                            contentColor = if (isSelected) Color.White else OceanOnSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("speed_${speed}x")
                    ) {
                        Text("${speed}x", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Right Controls: Run / Pause + Reset Trigger
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause/Resume FAB style
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) OceanSecondary else BrandPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play Control",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "توقف" else "شروع",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Reset Button
                OutlinedButton(
                    onClick = onReset,
                    border = BorderStroke(1.dp, ThemeOutline),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OceanOnSurface),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reset_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset Simulation", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("بازنشانی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// 2D ACOUSTIC ECOSYSTEM PLAYGROUND
@Composable
fun EcosystemPlayground(
    fishList: List<FishAgent>,
    sharkList: List<SharkAgent>,
    onAddFish: () -> Unit,
    onAddShark: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate static bubbles rise rise rises
    val bubbles = remember {
        mutableStateListOf<DecoBubble>().apply {
            for (i in 0 until 12) {
                add(
                    DecoBubble(
                        x = Random.nextFloat() * 1000f,
                        y = Random.nextFloat() * 1000f,
                        speed = Random.nextFloat() * 3f + 1f,
                        radius = Random.nextFloat() * 4f + 2f
                    )
                )
            }
        }
    }

    // Tick the floating bubbles animation
    var timeTicks by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            timeTicks += 0.05f
            bubbles.forEach { b ->
                b.y -= b.speed
                if (b.y < -50f) {
                    b.y = 1050f
                    b.x = Random.nextFloat() * 1000f
                }
            }
            delay(30)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(4.dp, ThemeOutline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ecosystem_playground_card")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Interactive Sandbox Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                    .background(Color(0xFF1D1B20))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("ecosystem_canvas")
                ) {
                    val canvasW = size.width
                    val canvasH = size.height

                    // Drawing physical dot grid matrix matching radial background in HTML
                    val gridSpacing = 60f
                    var gx = 20f
                    while (gx < canvasW) {
                        var gy = 20f
                        while (gy < canvasH) {
                            drawCircle(
                                color = Color(0xFF49454F).copy(alpha = 0.25f),
                                radius = 2f,
                                center = Offset(gx, gy)
                            )
                            gy += gridSpacing
                        }
                        gx += gridSpacing
                    }

                    // Drawing light rays filtering down from surface
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(OceanPrimary.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(canvasW / 2f, 0f),
                            radius = canvasW * 1.1f
                        ),
                        topLeft = Offset(0f, 0f),
                        size = size
                    )

                    // Draw bubbles
                    bubbles.forEach { b ->
                        val bScaledX = (b.x / 1000f) * canvasW
                        val bScaledY = (b.y / 1000f) * canvasH
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = b.radius,
                            center = Offset(bScaledX, bScaledY)
                        )
                        // tiny bubble specular glint
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = maxOf(1f, b.radius * 0.3f),
                            center = Offset(bScaledX - b.radius * 0.3f, bScaledY - b.radius * 0.3f)
                        )
                    }

                    // Draw FISH (PREY) 🐟
                    fishList.forEach { fish ->
                        val fx = (fish.x / 1000f) * canvasW
                        val fy = (fish.y / 1000f) * canvasH
                        val fAngle = fish.angle

                        // Scale fish visuals slightly
                        val fishLen = 14f
                        val fishWidth = 8f

                        // Draw Caudal fin (tail wiggles dynamically based on time!)
                        val wiggleOffset = sin(timeTicks * 3.5f + fish.id) * 0.35f
                        val tailAngle = fAngle + PI.toFloat() + wiggleOffset
                        
                        val tailX = fx + cos(tailAngle) * 9f
                        val tailY = fy + sin(tailAngle) * 9f

                        val tailPath = Path().apply {
                            moveTo(fx, fy)
                            lineTo(tailX + cos(tailAngle + 0.5f) * 6f, tailY + sin(tailAngle + 0.5f) * 6f)
                            lineTo(tailX + cos(tailAngle - 0.5f) * 6f, tailY + sin(tailAngle - 0.5f) * 6f)
                            close()
                        }
                        drawPath(tailPath, OceanPrimary.copy(alpha = 0.85f))

                        // Draw fish body (oval oriented to heading direction)
                        val bodyPath = Path().apply {
                            val frontHeadX = fx + cos(fAngle) * (fishLen / 2f)
                            val frontHeadY = fy + sin(fAngle) * (fishLen / 2f)
                            val backTailX = fx - cos(fAngle) * (fishLen / 2f)
                            val backTailY = fy - sin(fAngle) * (fishLen / 2f)

                            val midLeftX = fx + cos(fAngle + PI.toFloat()/2f) * (fishWidth / 2f)
                            val midLeftY = fy + sin(fAngle + PI.toFloat()/2f) * (fishWidth / 2f)
                            val midRightX = fx + cos(fAngle - PI.toFloat()/2f) * (fishWidth / 2f)
                            val midRightY = fy + sin(fAngle - PI.toFloat()/2f) * (fishWidth / 2f)

                            moveTo(frontHeadX, frontHeadY)
                            cubicTo(midLeftX, midLeftY, midLeftX, midLeftY, backTailX, backTailY)
                            cubicTo(midRightX, midRightY, midRightX, midRightY, frontHeadX, frontHeadY)
                        }
                        drawPath(bodyPath, OceanPrimary)

                        // Draw Fish eye (tiny dot in white at head)
                        val eyeX = fx + cos(fAngle) * 5f + cos(fAngle + PI.toFloat()/2f) * 2f
                        val eyeY = fy + sin(fAngle) * 5f + sin(fAngle + PI.toFloat()/2f) * 2f
                        drawCircle(Color.White, 1.8f, Offset(eyeX, eyeY))
                        drawCircle(Color.Black, 0.8f, Offset(eyeX, eyeY))
                    }

                    // Draw SHARKS (PREDATOR) 🦈
                    sharkList.forEach { shark ->
                        val sx = (shark.x / 1000f) * canvasW
                        val sy = (shark.y / 1000f) * canvasH
                        val sAngle = shark.angle

                        val sharkLen = 30f
                        val sharkWidth = 14f

                        // Draw Shark body (pointed predator design)
                        val sBodyPath = Path().apply {
                            val snoutX = sx + cos(sAngle) * (sharkLen * 0.6f)
                            val snoutY = sy + sin(sAngle) * (sharkLen * 0.6f)

                            val tailBaseX = sx - cos(sAngle) * (sharkLen * 0.5f)
                            val tailBaseY = sy - sin(sAngle) * (sharkLen * 0.5f)

                            val leftHipX = sx + cos(sAngle + 2.3f) * (sharkWidth * 0.5f)
                            val leftHipY = sy + sin(sAngle + 2.3f) * (sharkWidth * 0.5f)

                            val rightHipX = sx + cos(sAngle - 2.3f) * (sharkWidth * 0.5f)
                            val rightHipY = sy + sin(sAngle - 2.3f) * (sharkWidth * 0.5f)

                            moveTo(snoutX, snoutY)
                            lineTo(leftHipX, leftHipY)
                            lineTo(tailBaseX, tailBaseY)
                            lineTo(rightHipX, rightHipY)
                            close()
                        }
                        // Shark color scales dynamically as they starve: red glow!
                        val energyRatio = (shark.energy / 100f).coerceIn(0f, 1f)
                        val sharkColor = Color.interpolate(Color(0xFFE53E3E), OceanSecondary, energyRatio)
                        drawPath(sBodyPath, sharkColor)

                        // Draw dorsal fin (on top of body)
                        val dorsalPath = Path().apply {
                            val c1 = sx - cos(sAngle) * 2f
                            val c2 = sy - sin(sAngle) * 2f
                            val finTipX = c1 + cos(sAngle - 2f) * 12f
                            val finTipY = c2 + sin(sAngle - 2f) * 12f
                            moveTo(sx, sy)
                            lineTo(finTipX, finTipY)
                            lineTo(sx - cos(sAngle) * 8f, sy - sin(sAngle) * 8f)
                        }
                        drawPath(dorsalPath, sharkColor.copy(alpha = 0.85f))

                        // Draw tail fin wiggler
                        val sWiggle = sin(timeTicks * 2f + shark.id) * 0.25f
                        val sTailAngle = sAngle + PI.toFloat() + sWiggle
                        val sTailX = sx + cos(sTailAngle) * 15f
                        val sTailY = sy + sin(sTailAngle) * 15f
                        
                        val sTailPath = Path().apply {
                            moveTo(sx - cos(sAngle) * 12f, sy - sin(sAngle) * 12f)
                            lineTo(sTailX + cos(sTailAngle + 0.6f) * 12f, sTailY + sin(sTailAngle + 0.6f) * 12f)
                            lineTo(sTailX + cos(sTailAngle - 0.6f) * 12f, sTailY + sin(sTailAngle - 0.6f) * 12f)
                            close()
                        }
                        drawPath(sTailPath, sharkColor.copy(alpha = 0.90f))

                        // Draw glowing hunter eyes
                        val eyeLeftX = sx + cos(sAngle + 0.5f) * 8f
                        val eyeLeftY = sy + sin(sAngle + 0.5f) * 8f
                        drawCircle(Color(0xFFFFEE58), 2.2f, Offset(eyeLeftX, eyeLeftY))
                        drawCircle(Color.Black, 1f, Offset(eyeLeftX, eyeLeftY))

                        // Draw mini Energy ring around shark
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = 24f,
                            center = Offset(sx, sy),
                            style = Stroke(width = 1.5f)
                        )
                        drawArc(
                            color = OceanPrimary,
                            startAngle = -90f,
                            sweepAngle = 360f * energyRatio,
                            useCenter = false,
                            topLeft = Offset(sx - 24f, sy - 24f),
                            size = Size(48f, 48f),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }

            // Lower Bar containing Instant Manual triggers inside the sandbox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onAddFish,
                        colors = ButtonDefaults.buttonColors(containerColor = OceanPrimary.copy(alpha = 0.25f), contentColor = OceanPrimary),
                        border = BorderStroke(1.dp, OceanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp)
                            .testTag("inject_fish_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add fish", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تزریق ماهی (+🐟)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAddShark,
                        colors = ButtonDefaults.buttonColors(containerColor = OceanSecondary.copy(alpha = 0.25f), contentColor = OceanSecondary),
                        border = BorderStroke(1.dp, OceanSecondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp)
                            .testTag("inject_shark_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add shark", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تزریق کوسه (+🦈)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ANALYTICAL DETAILED SUB VIEW (DELETES AGENT RENDERING FOR DEEP MATHEMATICAL INSIGHTS)
@Composable
fun AnalyticalDetailsView(
    odeHistory: List<SimHistoryPoint>,
    peakPrey: Float,
    peakPredator: Float,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Continuous Rate of Change Math Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = OceanDarkSurface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ThemeOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "آهنگ تغییر پیوسته جمعیت‌ها (معادلات دیفرانسیل لوتکا-ولترا):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = BrandPrimary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Equation for Fish (Prey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "dX/dt = rX - aXY",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = OceanPrimary
                        )
                        Text(
                            text = "(ماهی)",
                            fontSize = 10.sp,
                            color = OceanOnSurface.copy(alpha = 0.6f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(ThemeOutline)
                    )

                    // Equation for Shark (Predator)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "dY/dt = -sY + bXY",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252)
                        )
                        Text(
                            text = "(کوسه)",
                            fontSize = 10.sp,
                            color = OceanOnSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // Charts container
        if (isLandscape) {
            // Landscape splits double graphs horizontally side-by-side
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(4.dp, ThemeOutline),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        PopulationChart(
                            history = odeHistory,
                            peakPrey = peakPrey,
                            peakPredator = peakPredator,
                            modifier = Modifier.fillMaxSize(),
                            title = "نمودار نوسان جمعیت در طول زمان (Population-Time)"
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(4.dp, ThemeOutline),
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        PhaseSpaceChart(
                            history = odeHistory,
                            peakPrey = peakPrey,
                            peakPredator = peakPredator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            // Portrait splits double graphs vertically
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(4.dp, ThemeOutline),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        PopulationChart(
                            history = odeHistory,
                            peakPrey = peakPrey,
                            peakPredator = peakPredator,
                            modifier = Modifier.fillMaxSize(),
                            title = "نمودار ریاضی جمعیت‌ها نسبت به زمان"
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1B20)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(4.dp, ThemeOutline),
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        PhaseSpaceChart(
                            history = odeHistory,
                            peakPrey = peakPrey,
                            peakPredator = peakPredator,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// 1. POPULATION VS TIME CUSTOM CANVAS CHART
@Composable
fun PopulationChart(
    history: List<SimHistoryPoint>,
    peakPrey: Float,
    peakPredator: Float,
    modifier: Modifier = Modifier,
    title: String = "نمودار جمعیتی"
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Legends
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(OceanSecondary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("کوسه (Y)", fontSize = 10.sp, color = OceanSecondary, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(OceanPrimary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ماهی (X)", fontSize = 10.sp, color = OceanPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Right
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("population_chart_canvas")
        ) {
            val w = size.width
            val h = size.height

            val padLeft = 40f
            val padRight = 10f
            val padTop = 15f
            val padBottom = 20f

            val graphW = w - padLeft - padRight
            val graphH = h - padTop - padBottom

            if (history.isEmpty()) return@Canvas

            // Scale calculations (bounding maximums)
            val maxPopulationY = maxOf(10f, maxOf(peakPrey, peakPredator) * 1.05f)

            // Draw axis lines & grid backgrounds
            // 4 Grid Lines
            val gridCount = 4
            for (i in 0..gridCount) {
                val relY = padTop + (graphH * i / gridCount)
                val gridVal = maxPopulationY * (gridCount - i) / gridCount

                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(padLeft, relY),
                    end = Offset(w - padRight, relY),
                    strokeWidth = 1f
                )

                // Grid vertical coordinates values
                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(120, 255, 255, 255)
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText(
                        gridVal.roundToInt().toString(),
                        padLeft - 8f,
                        relY + 8f,
                        textPaint
                    )
                }
            }

            // Draw Paths!
            val preyPoints = ArrayList<Offset>()
            val predPoints = ArrayList<Offset>()

            val pointsCount = history.size
            for (i in history.indices) {
                val pt = history[i]
                val rx = padLeft + (i.toFloat() / (pointsCount - 1).coerceAtLeast(1)) * graphW
                
                val preyY = padTop + graphH - (pt.prey / maxPopulationY) * graphH
                val predY = padTop + graphH - (pt.predator / maxPopulationY) * graphH

                preyPoints.add(Offset(rx, preyY))
                predPoints.add(Offset(rx, predY))
            }

            // Drawing Prey sequence (Fishes)
            if (preyPoints.size > 1) {
                val preyPath = Path().apply {
                    moveTo(preyPoints[0].x, preyPoints[0].y)
                    for (i in 1 until preyPoints.size) {
                        lineTo(preyPoints[i].x, preyPoints[i].y)
                    }
                }
                drawPath(preyPath, OceanPrimary, style = Stroke(width = 4.5f, join = StrokeJoin.Round))

                // Under path cyan ambient fading gradient
                val preyFillPath = Path().apply {
                    addPath(preyPath)
                    lineTo(preyPoints.last().x, padTop + graphH)
                    lineTo(preyPoints.first().x, padTop + graphH)
                    close()
                }
                drawPath(
                    preyFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(OceanPrimary.copy(alpha = 0.12f), Color.Transparent),
                        startY = padTop,
                        endY = padTop + graphH
                    )
                )
            }

            // Drawing Predator sequence (Sharks)
            if (predPoints.size > 1) {
                val predPath = Path().apply {
                    moveTo(predPoints[0].x, predPoints[0].y)
                    for (i in 1 until predPoints.size) {
                        lineTo(predPoints[i].x, predPoints[i].y)
                    }
                }
                drawPath(predPath, OceanSecondary, style = Stroke(width = 4.5f, join = StrokeJoin.Round))

                // Under path coral ambient fading gradient
                val predFillPath = Path().apply {
                    addPath(predPath)
                    lineTo(predPoints.last().x, padTop + graphH)
                    lineTo(predPoints.first().x, padTop + graphH)
                    close()
                }
                drawPath(
                    predFillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(OceanSecondary.copy(alpha = 0.10f), Color.Transparent),
                        startY = padTop,
                        endY = padTop + graphH
                    )
                )
            }

            // Draw bottom Baseline
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(padLeft, padTop + graphH),
                end = Offset(w - padRight, padTop + graphH),
                strokeWidth = 2f
            )
        }
    }
}

// 2. CLASSIC PHASE-SPACE REPRESENTATION (Y vs X) - CYCLICAL ORBITS
@Composable
fun PhaseSpaceChart(
    history: List<SimHistoryPoint>,
    peakPrey: Float,
    peakPredator: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "دیاگرام فضا-فاز (نمودار کوسه نسبت به ماهی)",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "حرکت چرخه‌ای اربیت‌ها تعادل اکولوژیکی را اثبات می‌کند",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("phase_space_canvas")
        ) {
            val w = size.width
            val h = size.height

            val padLeft = 40f
            val padBottom = 35f
            val padTop = 15f
            val padRight = 15f

            val graphW = w - padLeft - padRight
            val graphH = h - padTop - padBottom

            // Scale calculations (Y is Predator, X is Prey)
            val maxX = maxOf(10f, peakPrey * 1.05f)
            val maxY = maxOf(10f, peakPredator * 1.05f)

            // Draw axis lines
            // Y-Axis
            drawLine(Color.White.copy(alpha = 0.20f), Offset(padLeft, padTop), Offset(padLeft, padTop + graphH), strokeWidth = 2f)
            // X-Axis
            drawLine(Color.White.copy(alpha = 0.20f), Offset(padLeft, padTop + graphH), Offset(padLeft + graphW, padTop + graphH), strokeWidth = 2f)

            // Draw axis labels
            drawContext.canvas.nativeCanvas.apply {
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(100, 255, 255, 255)
                    textSize = 21f
                }
                // X label (Prey / Fish) -> Horizontal bottom
                drawText(
                    "ماهی (Prey X)",
                    padLeft + graphW * 0.45f,
                    padTop + graphH + 28f,
                    textPaint
                )
                // Y label (Predator / Sharks) -> Vertical top
                drawText(
                    "کوسه (Pred Y)",
                    padLeft + 10f,
                    padTop + 12f,
                    textPaint
                )
            }

            if (history.size < 2) return@Canvas

            // Plot orbital trace path (chronological sequence of (prey, predator))
            val orbitPath = Path()
            val points = history.map { pt ->
                val px = padLeft + (pt.prey / maxX) * graphW
                val py = padTop + graphH - (pt.predator / maxY) * graphH
                Offset(px, py)
            }

            orbitPath.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                orbitPath.lineTo(points[i].x, points[i].y)
            }

            // Draw flowing closed trajectory orbit
            drawPath(
                path = orbitPath,
                brush = Brush.horizontalGradient(listOf(OceanPrimary, OceanSecondary)),
                style = Stroke(width = 3.5f, join = StrokeJoin.Round)
            )

            // Draw dynamic glowing pulse dot at the CURRENT state (the very last calculated point)
            val lastPt = points.last()
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = lastPt
            )
            drawCircle(
                color = OceanSecondary,
                radius = 12f,
                center = lastPt,
                style = Stroke(width = 2f)
            )
        }
    }
}

// SLIDERS CONFIG PANEL: SYSTEM CONSTANTS & BIOLOGICAL RATES
@Composable
fun ParametersSection(
    r: Float,
    onRChange: (Float) -> Unit,
    a: Float,
    onAChange: (Float) -> Unit,
    s: Float,
    onSChange: (Float) -> Unit,
    b: Float,
    onBChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OceanDarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThemeOutline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inline formula badge from HTML: dY/dt = -sY + bXY
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "dY/dt = -sY + bXY",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = BrandOnContainer
                    )
                }

                Text(
                    text = "تنظیمات بیولوژیکی (System Coefficients)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BrandPrimary,
                    textAlign = TextAlign.Right
                )
            }

            // Sliders listing or continuous multipliers
            // 1. Prey growth rate 'r'
            ParameterSliderItem(
                label = "نرخ رشد طبیعی ماهی‌ها (r) - صید:",
                subText = "سرعت رشد ذاتی صید در صورت نبودن کوسه",
                value = r,
                range = 0.1f..1.5f,
                color = OceanPrimary,
                onValueChange = onRChange,
                testTag = "slider_r"
            )

            // 2. Hunting coefficient 'a'
            ParameterSliderItem(
                label = "نرخ صید ماهی‌ها توسط کوسه (a):",
                subText = "شدت برخورد و افت جمعیت صید ناشی از شکار",
                value = a,
                range = 0.01f..0.12f,
                color = OceanSecondary,
                onValueChange = onAChange,
                testTag = "slider_a"
            )

            // 3. Predator death rate 's'
            ParameterSliderItem(
                label = "نرخ مرگ طبیعی کوسه‌ها (s) - صیاد:",
                subText = "سرعت مرگ و گرسنگی صیاد به دلیل کمبود شکار",
                value = s,
                range = 0.1f..1.5f,
                color = Color(0xFFFF5252),
                onValueChange = onSChange,
                testTag = "slider_s"
            )

            // 4. Predator reproduction conversion 'b'
            ParameterSliderItem(
                label = "بازدهی تولید مثل صیاد پس از تغذیه (b):",
                subText = "ضریب تبدیل شکارهای صید به متولدین جدید کوسه",
                value = b,
                range = 0.005f..0.05f,
                color = BrandPrimary,
                onValueChange = onBChange,
                testTag = "slider_b"
            )
        }
    }
}

@Composable
fun ParameterSliderItem(
    label: String,
    subText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%.3f", value),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = color
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = OceanOnSurface.copy(alpha = 0.9f),
                    textAlign = TextAlign.Right
                )
                Text(
                    text = subText,
                    fontSize = 9.sp,
                    color = TextSubtitle
                )
            }
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = ThemeOutline
            ),
            modifier = Modifier
                .height(30.dp)
                .testTag(testTag)
        )
    }
}

// SIMULATION SEED ENVELOPE (INITIAL NUMERICAL SEEDS)
@Composable
fun SeedsSection(
    initPrey: Float,
    onInitPreyChange: (Float) -> Unit,
    initPredator: Float,
    onInitPredatorChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OceanDarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThemeOutline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "جمعیت اولیه (Initial Baseline Populations)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BrandPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // Seed Sliders
            SeedSliderItem(
                label = "تعداد اولیه ماهی‌ها (X₀):",
                value = initPrey,
                range = 5f..150f,
                color = OceanPrimary,
                onValueChange = onInitPreyChange,
                testTag = "slider_init_prey"
            )

            SeedSliderItem(
                label = "تعداد اولیه کوسه‌ها (Y₀):",
                value = initPredator,
                range = 2f..50f,
                color = OceanSecondary,
                onValueChange = onInitPredatorChange,
                testTag = "slider_init_pred"
            )
        }
    }
}

@Composable
fun SeedSliderItem(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${value.roundToInt()} موجود",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = color
            )
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = OceanOnSurface.copy(alpha = 0.85f)
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = ThemeOutline
            ),
            modifier = Modifier
                .height(28.dp)
                .testTag(testTag)
        )
    }
}

// STATISTICAL FEEDBACK DASHBOARD CARD
@Composable
fun StatisticsSection(
    fishCount: Float,
    sharkCount: Float,
    fishBirth: Int,
    fishDeath: Int,
    sharkBirth: Int,
    sharkDeath: Int,
    peakP: Float,
    peakS: Float,
    isEcosystem: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OceanDarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThemeOutline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "آماره‌ها و عملکرد اکولوژیکی (Real-time Stats)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = BrandPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            // Grid displaying metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Predator Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanDarkBackground)
                        .border(1.dp, ThemeOutline, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("کوسه (صیاد 🦈)", fontSize = 11.sp, color = OceanSecondary, fontWeight = FontWeight.Bold)
                    AnimatedContent(
                        targetState = if (isEcosystem) "${sharkCount.roundToInt()}" else String.format("%.1f", sharkCount),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                        },
                        label = "shark_count_anim"
                    ) { countStr ->
                        Text(
                            text = countStr,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OceanOnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("تولد کلی: $sharkBirth", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                    Text("مرگ کلی: $sharkDeath", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                    Text("اوج جمعیت: ${peakS.roundToInt()}", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                }

                // Prey Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanDarkBackground)
                        .border(1.dp, ThemeOutline, RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("ماهی (صید 🐟)", fontSize = 11.sp, color = OceanPrimary, fontWeight = FontWeight.Bold)
                    AnimatedContent(
                        targetState = if (isEcosystem) "${fishCount.roundToInt()}" else String.format("%.1f", fishCount),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                        },
                        label = "fish_count_anim"
                    ) { countStr ->
                        Text(
                            text = countStr,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OceanOnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("تولد کلی: $fishBirth", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                    Text("خورده شده: $fishDeath", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                    Text("اوج جمعیت: ${peakP.roundToInt()}", fontSize = 9.sp, color = OceanOnSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// Color interpolation tool to draw heat gradients dynamically
fun Color.Companion.interpolate(from: Color, to: Color, ratio: Float): Color {
    val r = from.red + (to.red - from.red) * ratio
    val g = from.green + (to.green - from.green) * ratio
    val b = from.blue + (to.blue - from.blue) * ratio
    val a = from.alpha + (to.alpha - from.alpha) * ratio
    return Color(r, g, b, a)
}
