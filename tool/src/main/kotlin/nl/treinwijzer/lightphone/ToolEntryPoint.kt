package nl.treinwijzer.lightphone

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override val enablePushNotifications: Boolean = false
}
