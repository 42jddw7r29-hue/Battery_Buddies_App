package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BatteryBuddiesRepository
import com.example.data.ChatMessageEntity
import com.example.data.ChatRoomEntity
import com.example.data.api.GeminiApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ScreenType {
    object Welcome : ScreenType
    object Scanning : ScreenType
    object Diagnostics : ScreenType
    object Dashboard : ScreenType
    data class ChatRoom(val room: ChatRoomEntity) : ScreenType
}

data class DeviceSpecs(
    val batteryPct: Int = 100,
    val isCharging: Boolean = false,
    val batteryTemp: Float = 30.0f,
    val cpuBoard: String = "Unknown",
    val cpuAbis: String = "arm64-v8a",
    val manufacturer: String = "Google",
    val proximityDist: Float = 5.0f,
    val rawLightLux: Float = 100.0f,
    val touchGridPassed: Boolean = false,
    val screenCrackedMode: Boolean = false, // If simulated cracked matrix is turned on
    val diagnosedGrade: String = "A" // "A" (Perfect), "B" (Cracked / Dead Zones), "C" (Proximity Blocked/Glass Fused)
)

@Suppress("DEPRECATION")
class BatteryBuddiesViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val repository = BatteryBuddiesRepository(application)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Screen navigation state
    private val _currentScreen = MutableStateFlow<ScreenType>(ScreenType.Welcome)
    val currentScreen: StateFlow<ScreenType> = _currentScreen.asStateFlow()

    // Scanned device specs state
    private val _specsState = MutableStateFlow(DeviceSpecs())
    val specsState: StateFlow<DeviceSpecs> = _specsState.asStateFlow()

    // Loading/scanning simulation state
    private val _scanProgress = MutableStateFlow(0.0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scanDetailsText = MutableStateFlow("Initializing Scanner...")
    val scanDetailsText: StateFlow<String> = _scanDetailsText.asStateFlow()

    // Touch Grid Calibration (6x6)
    // List representing whether each of 36 grid cells has been swiped.
    private val _touchGridState = MutableStateFlow(List(36) { false })
    val touchGridState: StateFlow<List<Boolean>> = _touchGridState.asStateFlow()

    // Available Rooms matching user's current specs
    val matchedRooms: StateFlow<List<ChatRoomEntity>> = repository.getRooms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Room Messages Flow
    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    // Sensors
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null

    init {
        // Find hardware sensors
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        // Read direct hardware configurations on boot
        readHardwareDirect()
        registerSensors()
    }

    private fun registerSensors() {
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values.firstOrNull() ?: 5.0f
                _specsState.value = _specsState.value.copy(proximityDist = distance)
            }
            Sensor.TYPE_LIGHT -> {
                val lux = event.values.firstOrNull() ?: 100.0f
                _specsState.value = _specsState.value.copy(rawLightLux = lux)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    fun readHardwareDirect() {
        // Read battery sticky intent directly (without registering receiver)
        val context = getApplication<Application>()
        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        var pct = 42 // Default funny mock if battery information is absent
        var isCharging = false
        var temp = 29.5f

        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                pct = ((level.toFloat() / scale.toFloat()) * 100).toInt()
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).toFloat() / 10.0f
        }

        // Get processor info
        val cpuBoard = Build.BOARD ?: "Unknown"
        val abis = Build.SUPPORTED_ABIS?.firstOrNull() ?: "arm64-v8a"
        val manufacturer = Build.MANUFACTURER ?: "Google"

        _specsState.value = _specsState.value.copy(
            batteryPct = pct,
            isCharging = isCharging,
            batteryTemp = temp,
            cpuBoard = cpuBoard,
            cpuAbis = abis,
            manufacturer = manufacturer
        )
    }

    fun setScreenCrackedMode(enabled: Boolean) {
        val grade = if (enabled) "B" else "A"
        _specsState.value = _specsState.value.copy(
            screenCrackedMode = enabled,
            diagnosedGrade = grade
        )
    }

    fun toggleGridCell(index: Int) {
        if (index < 0 || index >= 36) return
        // If simulation mode is active and we are tapping dead zones, prevent activation
        if (_specsState.value.screenCrackedMode && (index == 14 || index == 21 || index == 22)) {
            // These cells are simulated "dead zones" due to a cracked screen!
            return
        }
        val current = _touchGridState.value.toMutableList()
        current[index] = true
        _touchGridState.value = current

        // Filter out dead zones if screenCrackedMode is on
        val requiredPassCount = if (_specsState.value.screenCrackedMode) 33 else 36
        val swipedCount = current.count { it }

        if (swipedCount >= requiredPassCount) {
            _specsState.value = _specsState.value.copy(touchGridPassed = true)
        }
    }

    fun skipOrCompleteDiagnostics() {
        val grade = if (_specsState.value.screenCrackedMode || _specsState.value.proximityDist < 1.0f) {
            "B (مكسور جزئياً / Cracked Matrix)"
        } else {
            "A (سليم بالكامل / Crystal Perfect)"
        }
        _specsState.value = _specsState.value.copy(
            touchGridPassed = true,
            diagnosedGrade = grade
        )
        navigateTo(ScreenType.Dashboard)
    }

    fun startHardwareScan() {
        navigateTo(ScreenType.Scanning)
        _scanProgress.value = 0.0f
        viewModelScope.launch {
            val steps = listOf(
                "Scanning Battery Hardware Levels..." to 0.15f,
                "Reading battery capacity & temperature state..." to 0.35f,
                "Diagnosing Board processor and CPU cores..." to 0.55f,
                "Testing touch screen grids & hardware sensors..." to 0.75f,
                "Compiling device digital signature..." to 0.90f,
                "Scanning active matches in your area..." to 1.0f
            )

            for (step in steps) {
                _scanDetailsText.value = step.first
                while (_scanProgress.value < step.second) {
                    kotlinx.coroutines.delay(100)
                    _scanProgress.value += 0.05f
                }
            }

            // Move to diagnostics screen to test cracked screen / touch / sensor if not tested yet
            if (!_specsState.value.touchGridPassed) {
                navigateTo(ScreenType.Diagnostics)
            } else {
                generateMatchedRoomsDB()
                navigateTo(ScreenType.Dashboard)
            }
        }
    }

    suspend fun generateMatchedRoomsDB() {
        // Save scan log
        repository.saveScanLog(
            batteryPct = _specsState.value.batteryPct,
            cpuInfo = "${_specsState.value.manufacturer} ${_specsState.value.cpuBoard} (${_specsState.value.cpuAbis})",
            screenGrade = _specsState.value.diagnosedGrade
        )

        // Clear existing rooms as specs might change
        repository.clearAllData()

        val pct = _specsState.value.batteryPct
        val rawCpu = _specsState.value.cpuBoard.uppercase()
        val abis = _specsState.value.cpuAbis.lowercase()

        // Generate actual eligible rooms based on specifications!
        // 1. Exact Battery Matching Room
        repository.createAndJoinRoom(
            roomId = "battery_$pct",
            nameAr = "غرفة الـ %$pct شحناً الغامضة",
            nameEn = "The Mysterious $pct% Battery Club",
            type = "BATTERY",
            matchValue = pct.toString()
        )

        // 2. Hardware Engine Matching Room
        val (cpuAr, cpuEn) = when {
            rawCpu.contains("SNAP") || rawCpu.contains("SM") || rawCpu.contains("SD") -> {
                "سنابدراجون الحارقة للغلاف" to "Snapdragon Combustion Club"
            }
            rawCpu.contains("TENSOR") || rawCpu.contains("GS") -> {
                "أجهزة تينسور للذكاء الخارق" to "Tensor Oracle League"
            }
            rawCpu.contains("EXYNOS") -> {
                "نخبة بروسيسور إكسينوس للتبريد" to "Exynos Ice-Cooled Elite"
            }
            else -> {
                "جماعة معالجات الـ $abis النادرة" to "Rare $abis Processing Nexus"
            }
        }

        repository.createAndJoinRoom(
            roomId = "processor_${_specsState.value.cpuBoard.replace(" ", "_")}",
            nameAr = cpuAr,
            nameEn = cpuEn,
            type = "PROCESSOR",
            matchValue = _specsState.value.cpuBoard
        )

        // 3. Screen Calibration / Sensor Room
        if (_specsState.value.diagnosedGrade.contains("B") || _specsState.value.screenCrackedMode) {
            repository.createAndJoinRoom(
                roomId = "screen_broken",
                nameAr = "شلة الزجاج المتصدع والتاتش الخربان",
                nameEn = "The Crack Screen Fused Matrix",
                type = "SCREEN",
                matchValue = "Cracked Touch"
            )
        } else {
            repository.createAndJoinRoom(
                roomId = "screen_crystal",
                nameAr = "نادي الشاشات الكريستالية اللامعة",
                nameEn = "Crystal Clear Screen Alliance",
                type = "SCREEN",
                matchValue = "Pure Glass"
            )
        }
    }

    fun navigateTo(screen: ScreenType) {
        _currentScreen.value = screen
        if (screen is ScreenType.ChatRoom) {
            _activeRoomId.value = screen.room.roomId
            // Observe messages for this active room
            viewModelScope.launch {
                repository.getMessages(screen.room.roomId).collect {
                    activeRoomMessages.value = it
                }
            }
        } else {
            _activeRoomId.value = null
        }
    }

    fun sendMessage(roomId: String, roomType: String, matchValue: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSendingMessage.value = true
            try {
                // Save user message and trigger response
                val userMsg = repository.sendUserMessage(roomId, text, roomType, matchValue)

                // Retrieve updated conversation history for Gemini's context
                val currentHistory = activeRoomMessages.value

                repository.generateBuddyReplies(
                    roomId = roomId,
                    roomType = roomType,
                    matchValue = matchValue,
                    userMessage = text,
                    conversationHistory = currentHistory
                )
            } catch (e: Exception) {
                Log.e("BatteryBuddies", "Error sending message...", e)
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun leaveCurrentRoom(roomId: String) {
        viewModelScope.launch {
            repository.leaveRoom(roomId)
            navigateTo(ScreenType.Dashboard)
        }
    }

    fun resetTouchGrid() {
        _touchGridState.value = List(36) { false }
        _specsState.value = _specsState.value.copy(touchGridPassed = false)
    }

    override fun onCleared() {
        super.onCleared()
        unregisterSensors()
    }
}
