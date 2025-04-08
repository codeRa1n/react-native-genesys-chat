package com.genesyschat

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import com.facebook.react.ReactActivity
import com.facebook.react.bridge.ReadableMap
import com.genesys.cloud.core.utils.NRError
import com.genesys.cloud.integration.core.AccountInfo
import com.genesys.cloud.integration.core.StateEvent
import com.genesys.cloud.integration.messenger.MessengerAccount
import com.genesys.cloud.ui.structure.controller.ChatController
import com.genesys.cloud.ui.structure.controller.ChatEventListener
import com.genesys.cloud.ui.structure.controller.ChatLoadResponse
import com.genesys.cloud.ui.structure.controller.ChatLoadedListener


class GenesysChatActivity : ReactActivity(), ChatEventListener {

    private lateinit var chatController: ChatController
    private lateinit var account: AccountInfo
    private lateinit var onError: ((NRError) -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_layout)
        onError = reactInstanceManager.currentReactContext::emitError
        initToolbar()
        initAccount()
        createChat()
    }

    private fun initToolbar(){
        val toolbar = findViewById<Toolbar>(R.id.chat_toolbar);
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Esri Chat Support"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun initAccount() {
        val attributes = intent.getSerializableExtra(Attributes) as? HashMap<String, String>
        account = MessengerAccount(
                deploymentId = intent.getStringExtra(DeploymentId, ""),
                domain = intent.getStringExtra(Domain, "")
        ).apply {
            if (attributes != null) {
//                Log.d("Attributes", attributes?.toString() ?: "Attributes is null")
                customAttributes = attributes
            }
            logging = intent.getBooleanExtra(Logging, false)
        }
    }

    private fun createChat() {
        chatController = ChatController.Builder(this).build(account, object : ChatLoadedListener {

            override fun onComplete(result: ChatLoadResponse) {
                result.error?.let{
                    onError(it)
                    finish()
                }
                result.fragment?.let {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.chat_container, it, CONVERSATION_FRAGMENT_TAG)
                            .commit()
                }

                val progressBar = findViewById<ProgressBar>(R.id.waiting)
                progressBar.visibility = GONE
            }
        })
    }

    override fun onError(error: NRError) {
        super.onError(error)
        Log.e(GenTag, error.description ?: error.reason ?: error.errorCode)
        onError.invoke(error)
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 1) {
            destructChat()
        }
        super.onBackPressed()
    }

    override fun onChatStateChanged(stateEvent: StateEvent) {
        Log.i(GenTag, "Got Chat state: ${stateEvent.state}")
        when (stateEvent.state) {
            StateEvent.Ended -> {
                reactInstanceManager.currentReactContext.emitState(StateEvent.Ended)
                finish()
            }

            StateEvent.Started -> {
                findViewById<ProgressBar>(R.id.waiting)?.visibility = View.GONE
                reactInstanceManager.currentReactContext.emitState(StateEvent.Started)
            }

        }
        super.onChatStateChanged(stateEvent)
    }

    private fun destructChat() {
        chatController?.takeUnless { it.wasDestructed }?.run {
            terminateChat()
            destruct()
        }
    }

    override fun onStop() {
        super.onStop()

        if (isFinishing) {
            destructChat()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(GenTag, "onConfigurationChanged:")
    }

    companion object {
        const val CONVERSATION_FRAGMENT_TAG = "conversation_fragment"
        const val GenTag = "GenesysChatActivity"
        const val DeploymentId = "deploymentId"
        const val Domain = "domain"
        const val Attributes = "attributes"
        const val Logging = "logging"

        fun intentFactory(
            deploymentId: String,
            domain: String,
            attributes: ReadableMap,
            logging: Boolean
        ): Intent {

            return Intent("com.intent.action.Messenger_CHAT").apply {
                putExtra(DeploymentId, deploymentId)
                putExtra(Domain, domain)
                attributes?.let { putExtra(Attributes, it.toHashMap()) }
                putExtra(Logging, logging)
            }
        }

        private fun Intent.getStringExtra(key: String, fallback: String?): String {
            return getStringExtra(key) ?: fallback ?: ""
        }
    }
}