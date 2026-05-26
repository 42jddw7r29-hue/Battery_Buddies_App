package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GeminiApiClient
import com.example.data.api.Part
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.random.Random

@JsonClass(generateAdapter = true)
data class SimulatedReply(
    val senderName: String,
    val messageText: String
)

@JsonClass(generateAdapter = true)
data class SimulatedResponse(
    val replies: List<SimulatedReply>
)

class BatteryBuddiesRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val chatRoomDao = db.chatRoomDao()
    private val chatMessageDao = db.chatMessageDao()
    private val scanLogDao = db.scanLogDao()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val responseAdapter = moshi.adapter(SimulatedResponse::class.java)

    // Flow APIs
    fun getRooms(): Flow<List<ChatRoomEntity>> = chatRoomDao.getAllRooms()
    fun getMessages(roomId: String): Flow<List<ChatMessageEntity>> = chatMessageDao.getMessagesForRoom(roomId)
    fun getScanLogs(): Flow<List<ScanLogEntity>> = scanLogDao.getScanLogs()

    suspend fun saveScanLog(batteryPct: Int, cpuInfo: String, screenGrade: String) = withContext(Dispatchers.IO) {
        val log = ScanLogEntity(
            batteryPct = batteryPct,
            cpuInfo = cpuInfo,
            screenGrade = screenGrade
        )
        scanLogDao.insertScanLog(log)
    }

    suspend fun createAndJoinRoom(roomId: String, nameAr: String, nameEn: String, type: String, matchValue: String): ChatRoomEntity = withContext(Dispatchers.IO) {
        val room = ChatRoomEntity(
            roomId = roomId,
            roomNameAr = nameAr,
            roomNameEn = nameEn,
            roomType = type,
            matchValue = matchValue
        )
        chatRoomDao.insertRoom(room)

        // Add a friendly greeting message from system or a buddy when joining
        val currentMsgs = chatMessageDao.getMessagesForRoom(roomId)
        // If there are no messages, put a welcome buddy greeting
        val welcomeSender = when(type) {
            "BATTERY" -> "شريك الشحن #$matchValue"
            "PROCESSOR" -> "سنابدراجون المشتعل #55"
            else -> "أبو الفطور السري #12"
        }
        val welcomeText = when(type) {
            "BATTERY" -> "يا هلا بيك! دخلت الغرفة لأن شحنك هم $matchValue% بالضبط. سولفلي بطاريتك أصلية لو تعبانة؟ 🔋😂"
            "PROCESSOR" -> "أهلاً بعضو عائلة المعالجات القوية! جهازك السنابدراجون شلونه بالحرارة هسة؟ صوبة لو ماشي حاله؟ 🔥💻"
            else -> "هلا بالضلع المكسور شاشته! سويت فحص اللمس لو شاشتك مابيها شي ودتعبر علينا؟ الحك صبعك لا ينجرح بالجامات هههه 🩹📱"
        }

        chatMessageDao.insertMessage(
            ChatMessageEntity(
                roomId = roomId,
                senderName = welcomeSender,
                messageText = welcomeText,
                isUser = false
            )
        )

        room
    }

    suspend fun sendUserMessage(roomId: String, messageText: String, roomType: String, matchValue: String): ChatMessageEntity = withContext(Dispatchers.IO) {
        // 1. Save user message locally
        val userMsg = ChatMessageEntity(
            roomId = roomId,
            senderName = "أنت (You)",
            messageText = messageText,
            isUser = true
        )
        chatMessageDao.insertMessage(userMsg)
        userMsg
    }

    suspend fun generateBuddyReplies(roomId: String, roomType: String, matchValue: String, userMessage: String, conversationHistory: List<ChatMessageEntity>) = withContext(Dispatchers.IO) {
        // 2. Fetch Gemini response or simulated fallback
        if (GeminiApiClient.isApiKeyConfigured()) {
            try {
                val apiKey = GeminiApiClient.getApiKey()

                // Compile history for context
                val historyString = conversationHistory.takeLast(10).joinToString("\n") {
                    "${it.senderName}: ${it.messageText}"
                }

                val systemPrompt = """
                    You are simulating a group chat of anonymous hardware-matched users in 'Battery Buddies'.
                    The current room is type '$roomType' with value '$matchValue'.
                    
                    CRITICAL INSTRUCTIONS:
                    1. Generate 1 or 2 distinct message replies from separate anonymous roommates.
                    2. Use funny and local Iraqi/Arabic names/tags for them (e.g., 'أبو الشحن 42', 'عاشق التبريد #99', 'مكسر الابتسامات #14', 'سيد البكسل الميت').
                    3. They should chat in a rich, funny blend of Iraqi/Arabic dialects ('ضلعي', 'هسة', 'ولك', 'قهرني البكسل', 'شحن سفري') about their hardware specifications, jokes, charging problems, and physical device quirks.
                    4. Respond ONLY with a valid JSON string adhering strictly to this schema:
                    {
                      "replies": [
                        { "senderName": "اسم العضو هنا", "messageText": "نص الرسالة باللهجة العراقية اللطيفة الكوميدية" }
                      ]
                    }
                    Do not wrap in ```json or any other markdown containers. Just return plain JSON.
                """.trimIndent()

                val prompt = """
                    Conversation History so far:
                    $historyString
                    
                    User just sent: "$userMessage"
                    
                    Generate the next humorous buddy responses matching the hardware spec '$matchValue'.
                """.trimIndent()

                // Call direct REST API via Retrofit Service
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = GeminiApiClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

                // Clean-up Gemini markdown wraps
                val cleanJson = responseText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                Log.d("BatteryBuddies", "Gemini response: $cleanJson")

                val simulatedResponse = responseAdapter.fromJson(cleanJson)
                if (simulatedResponse != null && simulatedResponse.replies.isNotEmpty()) {
                    for (reply in simulatedResponse.replies) {
                        chatMessageDao.insertMessage(
                            ChatMessageEntity(
                                roomId = roomId,
                                senderName = reply.senderName,
                                messageText = reply.messageText,
                                isUser = false
                            )
                        )
                    }
                    return@withContext
                }
            } catch (e: Exception) {
                Log.e("BatteryBuddies", "Error call Gemini API, falling back...", e)
            }
        }

        // --- Simulated Fallback ---
        // Let's sleep slightly to make it feel natural and real-time processing
        kotlinx.coroutines.delay(1000 + Random.nextLong(1500))

        val fallbackReplies = getPresetRepliesForRoom(roomType, matchValue, userMessage)
        for (reply in fallbackReplies) {
            chatMessageDao.insertMessage(
                ChatMessageEntity(
                    roomId = roomId,
                    senderName = reply.senderName,
                    messageText = reply.messageText,
                    isUser = false
                )
            )
        }
    }

    private fun getPresetRepliesForRoom(roomType: String, matchValue: String, userMessage: String): List<SimulatedReply> {
        return when (roomType) {
            "BATTERY" -> {
                val name1 = "أبو الشاحنة العاطلة #${Random.nextInt(10, 99)}"
                val name2 = "شحن سفري #${Random.nextInt(100, 999)}"
                val options1 = listOf(
                    "ولك ذب الشحن وسولف ويانة، البطارية الـ $matchValue% هاي أندر فصيلة بالكون ههههه!",
                    "آني هم $matchValue% وحاط الشاحن بالجنطة بس مالي خلك أكوم أشحن 😂",
                    "تدرون لو طفى تلفوني راح تروح الغرفة؟ ضل علي مروتك لا تشحن هسة 😂",
                    "سبحان الله، نفس مواصفات بطاريتي بالضبط، ربي يستر لا تنفجر علينا شواحن التجاري!"
                )
                val options2 = listOf(
                    "يا ضلعي آني هسة مشتعلة عندي الحرارة، تنصحوني أبقي على شحن لو أطلعه؟",
                    "أهلاً بالحبايب شلة الـ $matchValue%. الشاشة عاكسة الشحن مالي هههه عمي باركولي!",
                    "البلد كله طافي وطاقة الوطنية طفت وتلفوني شحنه $matchValue% شسوي بربكم؟ 🤦‍♂️",
                    "الحقني شاحني صيني ديفصل كل دقيقة، اكو أحد عنده شاحن أصلي؟"
                )
                listOf(
                    SimulatedReply(name1, options1.random()),
                    SimulatedReply(name2, options2.random())
                ).shuffled().take(Random.nextInt(1, 3))
            }
            "PROCESSOR" -> {
                val name1 = "المعالج الخارق #${Random.nextInt(10, 99)}"
                val name2 = "سنابدراجون_صوبة #${Random.nextInt(100, 999)}"
                val options1 = listOf(
                    "ولك جهازي سنابدراجون صاير طباخ سفري، جاي أحمي عليه الشاي هسة هههه 🔥☕",
                    "المعالج مالي ديبعث إشارة ترحيب باللي جهازهم نفس النواة، كفوو والله ضلعي!",
                    "تدرون هذا المعالج مالتنا أقوى شي بس بالصيف العراقي يتبخر من الحرارة 😂",
                    "كفو جماعة المعالجات القوية، الباقي مالهم صوت!"
                )
                val options2 = listOf(
                    "صاحبي، جهازك يسخن لو طبيعي؟ آني من أفتح اللعبة يصيح إلحقوووني هههه",
                    "كلنا بنفس المعالج يعني كلنا بنفس القارب الحار! خلونا نحط ثلج ورا الجهاز هههه ❄️",
                    "أقوى مواصفة معالج بالعراق بس الشبكة تعبانة شالفايدة؟ عمي فدوة لربكم 🤣",
                    "Snapdragon المشتعل يرحب بكل الشركاء، نورت الغرفة حبيب قلبي!"
                )
                listOf(
                    SimulatedReply(name1, options1.random()),
                    SimulatedReply(name2, options2.random())
                ).shuffled().take(Random.nextInt(1, 3))
            }
            else -> { // SCREEN
                val name1 = "ضلع مكسور #${Random.nextInt(10, 99)}"
                val name2 = "تاتش تعبان #${Random.nextInt(100, 999)}"
                val options1 = listOf(
                    "ولك آني أكتب والجامات دتجرح صبعي بس فدوة لغرفة المكسرين ههههه 🩹😂",
                    "شاشتي صايرة مثل خريطة بغداد من كثر الفطور! أهلاً بصديقي المكسور الشاشة",
                    "جماعة الشاشة المكسرة، تدرون آني مالي خلك أصلحها، هيج أحلى كشخة وبلاش هههه",
                    "سويت فحص كشف اللمس وطلع عندي المايكرو تاتش متوقف! شاشتي دتخرف هسة من وحدها"
                )
                val options2 = listOf(
                    "أهلاً بشريك الفطر! تدرون أحلى شي بالشاشة المكسورة محد يحسدك عليها وعيونهم تبتعد 😂",
                    "هل من ضحايا جدد هنا؟ تاتش جهازي صار غبي كل ما أكتب حرف يطلع مكانه خمسة!",
                    "الشاشة مكسرة واللمس يشتغل بالقدرة الباقية هههه، تباً للصين وصناعتهم!",
                    "جربت لصقة أم الـ 500 دينار لو مخليه على حاله أحسن؟ 😂🩹"
                )
                listOf(
                    SimulatedReply(name1, options1.random()),
                    SimulatedReply(name2, options2.random())
                ).shuffled().take(Random.nextInt(1, 3))
            }
        }
    }

    suspend fun leaveRoom(roomId: String) = withContext(Dispatchers.IO) {
        chatRoomDao.deleteRoom(roomId)
        chatMessageDao.deleteMessagesForRoom(roomId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        chatRoomDao.deleteAllRooms()
    }
}
