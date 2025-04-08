package com.genesyschat

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.genesys.cloud.core.utils.NRError
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.genesys.cloud.integration.messenger.MessengerAccount
import com.genesys.cloud.messenger.transport.core.Configuration
import com.genesys.cloud.messenger.transport.core.Message
import com.genesys.cloud.messenger.transport.core.MessageEvent
import com.genesys.cloud.messenger.transport.core.MessagingClient
import com.genesys.cloud.messenger.transport.core.MessagingClient.State
import com.genesys.cloud.messenger.transport.core.MessengerTransportSDK
import com.genesys.cloud.messenger.transport.util.DefaultVault
import com.genesys.cloud.ui.structure.controller.ChatController

internal fun ReactContext?.emitError(error: NRError) {
    this ?: return

    val event = Arguments.createMap().apply {
        putString("errorCode", error.errorCode)
        putString("reason", error.reason)
        putString("message", error.description)
    }

    getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("onMessengerError", event)
}

internal fun ReactContext?.emitState(state: String) {
    this ?: return

    val event = Arguments.createMap().apply {
        putString("state", state)
    }

    getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit("onMessengerState", event)
}


class GenesysChatModule(reactContext: ReactApplicationContext) :
        ReactContextBaseJavaModule(reactContext) {
    private var messengerTransport: MessengerTransportSDK? = null
    private var messagingClient: MessagingClient? = null
    private lateinit var chatController: ChatController

    override fun getName(): String {
        return NAME
    }

    private fun sendEvent(eventName:String, data: WritableMap) {
        try {
            reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit(eventName, data)
        }catch (e:Throwable){
            Log.d(MODULE_TAG, e.localizedMessage)
        }
    }

//    @ReactMethod
//    fun startChat(deploymentId: String, domain: String, attributes: ReadableMap, logging: Boolean) {
//        reactApplicationContext.let {
//            currentActivity?.run {
//                startActivity(
//                        GenesysChatActivity.intentFactory(deploymentId, domain, attributes, logging)
//                )
//            }
//        }
//    }

    @ReactMethod
    fun startChat(deploymentId: String, domain: String, attributes: ReadableMap? = null, logging: Boolean) {
        var account = setupAccount(deploymentId, domain)
        account.customAttributes = (attributes as? HashMap<String, String>)!!
        startChat(account)
    }

    private fun setupAccount(deploymentId: String, domain: String): MessengerAccount {
        return MessengerAccount(deploymentId, domain)
    }

    private fun startChat(account: MessengerAccount) {
        reactApplicationContext.let {
            currentActivity?.run {
                chatController = ChatController.Builder(this).build(account)
            }
        }
    }

    @ReactMethod
    fun setupMessengerTransport(deploymentId: String, domain: String, attributes: ReadableMap? = null, logging: Boolean) {
        val configuration = Configuration(deploymentId, domain, logging, 100000)
        DefaultVault.context = reactApplicationContext
        messengerTransport = MessengerTransportSDK(configuration)
        messagingClient = messengerTransport?.createMessagingClient().also { stateChange ->
            stateChange?.stateChangedListener = {
                // Handle state change events here.
                when (it.newState) {
                    State.Connecting -> {
                        Log.d(MODULE_TAG, "Establishing a secure connection via WebSocket.")
                    }

                    State.Connected -> {
                        Log.d(MODULE_TAG, "established")
                    }

                    is State.Configured -> {
                        Log.d(MODULE_TAG, "Configured")
                    }

                    else -> {
                        Log.d(MODULE_TAG, "Nothing listened")
                    }
                }
            }
            stateChange?.messageListener = {
                // Handle message events here
                when (it) {
                    is MessageEvent.MessageInserted -> {
                        val event = Arguments.createMap().apply {
                            putString("id", it.message.id)
                            putString("text", it.message.text ?: "")
                            putBoolean("isUser", it.message.from.originatingEntity == Message.Participant.OriginatingEntity.Human)
                        }
                        sendEvent("onGenesysMessage", event)
                    }
                    else -> {
                        Log.d(MODULE_TAG, "Unhandled event type: $it")
                    }
                }
            }
        }
        try {
            Log.d(MODULE_TAG, "Trying to connect")
            messagingClient?.connect() // Instruct MessagingClient to connect to a WebSocket.
        } catch (t: Throwable) {
            // Handle exceptions here.
            Log.e(MODULE_TAG, "connect: " + t.localizedMessage)
        }
    }

    @ReactMethod
    fun getConversation(promise: Promise) {
        messagingClient?.let {
            val conversation = it.conversation
            val conversationData = conversation.map { message ->
                mapOf(
                        "id" to message.id,
                        "direction" to if (message.direction == Message.Direction.Inbound) "Inbound" else "Outbound",
                        "type" to message.type,
                        "text" to message.text,
                        "timeStamp" to message.timeStamp,
                        "attachments" to message.attachments,
                        "events" to message.events,
                        "from" to mapOf(
                                "name" to (message.from.name ?: ""),
                                "imageUrl" to (message.from.imageUrl ?: ""),
                                "originatingEntity" to if (message.from.originatingEntity == Message.Participant.OriginatingEntity.Human) "Human" else "Bot"
                        ),
                        "isUser" to (message.from.originatingEntity == Message.Participant.OriginatingEntity.Human)
                )
            }
            Log.d(MODULE_TAG,"$conversationData")
            promise.resolve(conversationData)
        }
                ?: promise.reject("ClientNotInitialized", "Messaging client is not initialized.", null)
    }

    @ReactMethod
    fun sendMessage(query: String, attributes: ReadableMap?= null) {
        try {
            messagingClient?.let {
                it.sendMessage(query, (attributes as? HashMap<String, String>) ?: mapOf<String, String>())
            }
        } catch (t: Throwable) {
            // Handle exceptions here.
            Log.e(MODULE_TAG, "sendMessage: " + t.localizedMessage)
        }
    }

    @ReactMethod
    fun clearHistory() {
        try {
            messagingClient?.let {
                it.clearConversation()
            }
        } catch (t: Throwable) {
            // Handle exceptions here.
            Log.e(MODULE_TAG, "sendMessage: " + t.localizedMessage)
        }
    }

    @ReactMethod
    fun disconnectAndCleanup() {
        try {
            messagingClient?.let {
                it.clearConversation()
                it.disconnect()
            }
            messengerTransport = null
            messagingClient = null
            Log.d(MODULE_TAG, "cleared instance of client and transport sdk")
        } catch (t: Throwable) {
            // Handle exceptions here.
            Log.e(MODULE_TAG, "disconnectAndCleanup: " + t.localizedMessage)
        }
    }

    companion object {
        const val NAME = "GenesysChat"
        const val MODULE_TAG = "GenesysChatModule"
    }
}
