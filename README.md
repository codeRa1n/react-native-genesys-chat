## react-native-genesys-chat

A React Native plugin to integrate Genesys Chat SDK into your mobile applications, allowing you to manage chat sessions, send messages, and listen to events such as incoming messages, typing notifications, and more.

## Features

- Initialize chat sessions with Genesys.
- Send messages to the Genesys chat service.
- Listen for events like incoming messages and typing notifications.
- Clean up and disconnect chat sessions.

## Installation

To install the plugin, run:

```bash
npm install react-native-genesys-chat
```

## Linking

If you are using React Native 0.60 or above, the package should be automatically linked. For older versions, you may need to link the package manually:

```bash
react-native link react-native-genesys-chat
```

---

## Installation

For iOS, make sure to run:

1. Add these sources to your **Podfile**:

```ruby
source 'https://github.com/MyPureCloud/gc-sdk-specs'
source 'https://github.com/CocoaPods/Specs'
```

2. Then, run:

```bash
cd ios/
pod install
```

---

This ensures that you have the correct sources configured for the genesys pod installation.

## Usage

```javascript
import React, { useEffect, useState } from "react";
import {
  setupMessengerTransport,
  genesysChatEventEmitter,
  sendMessage,
  disconnectAndCleanup,
} from "react-native-genesys-chat";
import { View } from "react-native";

const ChatComponent = () => {
  const [chats, setChats] = useState([]);
  const [isAgentCompanion, setIsAgentCompanion] = useState(false);
  const attributes = {};

  useEffect(() => {
    // Initialize the SDK with your deployment and domain details
    setupMessengerTransport(
      "PROD", // Deployment environment, e.g., 'PROD'
      "usw2.pure.cloud" // Domain
    );

    // Start chat session
    sendMessage("init chat", attributes); // 'attributes' is a additional options

    // Event listeners
    const genMsgSubscription = genesysChatEventEmitter.addListener(
      "onGenesysMessage",
      (message) => {
        if (!isAgentCompanion && message.from === "human") {
          setIsAgentCompanion(true);
        }
        setChats((prevChats) => [...prevChats, message]);
      }
    );

    const genEventsSubscription = genesysChatEventEmitter.addListener(
      "onTyping",
      (events) => {
        console.log("Typing Event:", events);
      }
    );

    return () => {
      disconnectAndCleanup();
      genMsgSubscription.remove();
      genEventsSubscription.remove();
    };
  }, []);

  return <View>{/* Render chats or other UI components here */}</View>;
};

export default ChatComponent;
```

## API

### `setupMessengerTransport(deploymentId: string, domain: string, attributes?: { [key: string]: string }, logging: boolean = false)`

Sets up the messaging transport for the chat SDK.

**Parameters:**

- `deploymentId`: The deployment environment (e.g., 'PROD').
- `domain`: The domain for your Genesys environment (e.g., 'usw2.pure.cloud').
- `attributes`: Optional attributes to send with the session.
- `logging`: Optional boolean for enabling logging (default is `false`).

### `sendMessage(query: string, attributes?: { [key: string]: string })`

Sends a message to the Genesys chat service.

**Parameters:**

- `query`: The message you want to send.
- `attributes`: Optional custom attributes to attach to the message.

### `disconnectAndCleanup()`

Disconnects and cleans up the chat session when finished.

**Parameters:** None.

### Events

The package exposes several events that you can listen to using the `genesysChatEventEmitter`.

#### `onGenesysMessage`

- **Description**: Triggered when a new message is sent or received from the chat service.
- **Callback Signature**: `(message: Message) => void`
  - `message`: The message object containing details like `from`, `text`, etc.

#### `onTyping`

- **Description**: Triggered when a user is typing a message in the chat.
- **Callback Signature**: `(state: TypingState) => void`
  - `state`: The typing state object containing:
    - `isTyping`: A boolean indicating whether the user is typing.
    - `durationInMilliseconds`: (optional) The duration in milliseconds that the user has been typing.

#### `onMessengerState`

- **Description**: Triggered when the messenger state changes (e.g., started, ended).
- **Callback Signature**: `(state: MessengerState) => void`
  - `state`: The messenger state object containing:
    - `state`: The current state of the messenger, which can be `'started'` or `'ended'`.

#### `onMessengerError`

- **Description**: Triggered when an error occurs within the messenger.
- **Callback Signature**: `(error: unknown) => void`
  - `error`: The error object containing the details of the issue.

## Contributing

Contributions are welcome! Please feel free to fork this repository, create a branch, and submit pull requests. For any issues or feature requests, please open an issue in the GitHub repository.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
