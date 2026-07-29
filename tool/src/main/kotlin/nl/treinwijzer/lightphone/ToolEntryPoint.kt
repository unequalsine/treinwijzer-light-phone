package nl.treinwijzer.lightphone

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json

object PushBridge {
    val endpoint = MutableStateFlow<String?>(null)
    val signals = MutableSharedFlow<LightPushEnvelope>(replay = 1, extraBufferCapacity = 8)
}

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    private val json = Json { ignoreUnknownKeys = true }

    override val enablePushNotifications: Boolean = true

    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        serverData.collectLatest { data ->
            PushBridge.endpoint.value = data?.pushCredentials?.pushEndpoint
        }
    }

    override suspend fun onPushNotification(data: ByteArray) {
        val envelope = runCatching {
            json.decodeFromString<LightPushEnvelope>(data.decodeToString())
        }.getOrNull() ?: return
        if (envelope.version == 1) PushBridge.signals.emit(envelope)
    }
}
