import Foundation
import UIKit
import GenesysCloud
import GenesysCloudMessenger
import MessengerTransport
import React

import React

@objc(GenesysChat)
class GenesysChat: RCTEventEmitter, ChatControllerDelegate {
    var chatController: ChatController?
    var emitterHasListeners: Bool = false
    
    override func supportedEvents() -> [String]! {
        return ["onMessengerError", "onMessengerState", "onGenesysMessage", "onTyping"]
    }
    
    override func startObserving() {
        emitterHasListeners = true
        print("emitterHasListeners true")
    }
    
    override func stopObserving() {
        emitterHasListeners = false
        print("emitterHasListeners false")
    }

    override static func moduleName() -> String {
        return "GenesysChat"
    }
    
    @objc func startChat(_ deploymentId: String, domain: String, attributes: [String : String] = [:], logging: Bool) {
        let account = setupAccount(deploymentId: deploymentId, domain: domain, logging: logging)
        account.customAttributes = attributes
        startChat(with: account)
    }
    
    private func setupAccount(deploymentId: String, domain: String, logging: Bool) -> MessengerAccount {
        return MessengerAccount(deploymentId: deploymentId, domain: domain, logging: logging)
    }
    
    private func startChat(with account: MessengerAccount) {
        chatController = ChatController(account: account)
        chatController?.delegate = self
    }
    
    @objc func doneButtonPressed() {
        chatController?.terminate()
        guard let rootViewController = UIApplication.shared.keyWindow?.rootViewController else {
            return
        }
        rootViewController.dismiss(animated: true, completion: nil)
    }
    
    @objc func shouldPresentChatViewController(_ viewController: UINavigationController) {
        viewController.modalPresentationStyle = .overFullScreen
        let doneBarButtonItem = UIBarButtonItem(title: "End Chat", style: .done, target: self, action: #selector(doneButtonPressed))
        viewController.navigationBar.topItem?.rightBarButtonItem = doneBarButtonItem
        guard let rootViewController = UIApplication.shared.keyWindow?.rootViewController else {
            return
        }
        rootViewController.present(viewController, animated: true, completion: nil)
    }
    
    func didFail(with error: GCError!) {
        if emitterHasListeners {
            let errorCode: Int = error.errorType.rawValue;
            let reason: String = error.error.debugDescription
            let message: String = error.error?.localizedDescription ?? ""
            
            let errorInfo: [String: Any] = [
                "errorCode": errorCode,
                "reason": reason,
                "message": message
            ]
            sendEvent(withName: "onMessengerError", body: errorInfo)
        }
    }
    
    @objc func didUpdateState(_ event: ChatStateEvent) {
        var state: String?
        switch event.state {
        case .chatStarted:
            state = "started"
        case .chatEnded:
            state = "ended"
        default:
            break
        }
        
        if let state = state, emitterHasListeners {
            sendEvent(withName: "onMessengerState", body: ["state": state])
        }
    }
    
    private var messengerTransport: MessengerTransportSDK?
    private var client: MessagingClient?

    // Transport SDK
    @objc func setupMessengerTransport(_ deploymentId: String, domain: String, attributes: [String : String] = [:], logging: Bool) {
        if messengerTransport != nil {
            print("Already set up, no need to do it again")
            return
        }
        messengerTransport = MessengerTransportSDK(configuration: Configuration(deploymentId: deploymentId, domain: domain, logging: logging, reconnectionTimeoutInSeconds: 100000))
        client = messengerTransport?.createMessagingClient()
        // Inittiate connection to client
        do {
            print("Trying to connect")
            try client?.connect()
        } catch let error {
            print("ERROR: connect \(error)")
        }
        // Connection state event listner
        client?.stateChangedListener = { [weak self] stateChange in // Subscribe to socket states listener.
            switch stateChange.newState {
            case is MessagingClientState.Connecting:
                print("Establishing a secure connection via WebSocket.")
                break
            case is MessagingClientState.Connected:
                print("established")
                self?.sendEvent(withName: "onMessengerState", body: ["state":"started"])
                break
            case let configured as MessagingClientState.Configured:
                print("Configured",configured)
                break
            case is MessagingClientState.Closed:
                print("Closed")
                self?.sendEvent(withName: "onMessengerState", body: ["state":"ended"])
                break
            case is MessagingClientState.Closing:
                print("Closing....")
                break
            default:
                print("Nothing listened")
            }
        }
        // client messenger event listner
        client?.messageListener = { [weak self] event in
            switch event {
            case let inserted as MessageEvent.MessageInserted:
//                print("insert type>",inserted.message.direction)
//                print("inserted msg>",inserted.message.text ?? "")
//                print("inserted originatingEntity>",inserted.message.from.originatingEntity)
                let messageData: [String: Any] = [
                    "id": inserted.message.id,
                    "text": inserted.message.text ?? "",
                    "isUser": inserted.message.direction == .inbound,
                    "from": {
                        switch inserted.message.from.originatingEntity {
                        case .human:
                            return "human"
                        case Message.ParticipantOriginatingEntity.companion:
                            return "companion"
                        case .bot:
                            return "bot"
                        case .unknown:
                            return "unknown"
                        default:
                            return "values"
                        }
                    }()
                ]
                self?.sendEvent(withName: "onGenesysMessage", body: messageData)
            default:
                // Handle other event types
                print("Unhandled event type:", event)
            }
        }
        
        // client event listner
        client?.eventListener = { [weak self] event in
            switch event {
            case let inserted as Event.AgentTyping:
                let messageData: [String: Any] = ["isTyping": true, "durationInMilliseconds": inserted.durationInMilliseconds]
                self?.sendEvent(withName: "onTyping", body: messageData)
                print("Typing is in progress")

                // Schedule a task to reset the typing indicator after the specified duration
                DispatchQueue.main.asyncAfter(deadline: .now() + .milliseconds(Int(inserted.durationInMilliseconds))) { [weak self] in
                    let resetMessageData: [String: Any] = ["isTyping": false]
                    self?.sendEvent(withName: "onTyping", body: resetMessageData)
                    print("Typing indicator reset after duration")
                }
            default:
                print("Current event is not processed")
            }
        }
    }
    
    @objc func getConversation(_ resolve: RCTPromiseResolveBlock, rejecter reject: RCTPromiseRejectBlock) {
        guard let client = client else {
            reject("ClientNotInitialized", "Messaging client is not initialized.", nil)
            return
        }
        let conversation = client.conversation
                let conversationData = conversation.map { message -> [String: Any] in
                    var messageDict = [String: Any]()
                    messageDict["id"] = message.id
                    messageDict["direction"] = message.direction == Message.Direction.inbound ? "Inbound" : "Outbound"
                    messageDict["type"] = message.messageType
                    messageDict["text"] = message.text
                    messageDict["timeStamp"] = message.timeStamp
                    messageDict["attachments"] = message.attachments
                    messageDict["msgType"] = message.from.originatingEntity
                    messageDict["from"] = [
                        "name": message.from.name ?? "",
                        "imageUrl": message.from.imageUrl ?? "",
                        "originatingEntity": message.from.originatingEntity == Message.ParticipantOriginatingEntity.human ? "Human" : "Bot"
                    ]
                    messageDict["isUser"] = message.direction == Message.Direction.inbound
                    return messageDict
                }
                resolve(conversationData)
        }
    
    @objc func sendMessage(_ query: String, attributes: [String : String] = [:]){
        do {
            try client?.sendMessage(text: query,customAttributes: attributes)
        } catch let error{
            print("ERROR: sendMessage :\(error)")
        }
    }
    
    @objc func clearHistory(){
        do {
            try client?.clearConversation()
        } catch let error{
            print("ERROR: clearConversation: \(error)")
        }
    }
    
    @objc func disconnectAndCleanup() {
        do {
            clearHistory()
            try client?.disconnect()
            messengerTransport = nil
            client = nil
            emitterHasListeners = false
            print("cleared instance of client and transport sdk")
        } catch let error{
            print("ERROR: disconnectAndCleanup: \(error)")
        }
    }
    
    override static func requiresMainQueueSetup() -> Bool {
        return false
    }
}
