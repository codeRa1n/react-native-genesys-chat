import {
  NativeModules,
  Platform,
  NativeEventEmitter,
  DeviceEventEmitter,
} from "react-native";
import { Message } from "./types";

const LINKING_ERROR =
  "The package 'react-native-genesys' doesn't seem to be linked. Make sure: \n\n" +
  Platform.select({ ios: "- You have run 'pod install'\n", default: "" }) +
  "- You rebuilt the app after installing the package\n" +
  "- You are not using Expo Go\n";

const GenesysChat = NativeModules.GenesysChat
  ? NativeModules.GenesysChat
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

type MessengerState = { state: "started" | "ended" };
type TypingState = { isTyping: boolean; durationInMilliseconds?: number };
type GenesysMessage = Message;
type EventType =
  | "onMessengerState"
  | "onMessengerError"
  | "onGenesysMessage"
  | "onTyping";

type EventCallback<T extends EventType> = T extends "onMessengerState"
  ? (state: MessengerState) => void
  : T extends "onMessengerError"
  ? (error: unknown) => void
  : T extends "onGenesysMessage"
  ? (message: GenesysMessage) => void
  : T extends "onTyping"
  ? (state: TypingState) => void
  : never;

type EVENT_TYPES = {
  addListener<T extends EventType>(
    eventType: T,
    callback: EventCallback<T>
  ): { remove: () => void };
};

const genesysChatEventEmitter: EVENT_TYPES =
  Platform.OS === "android"
    ? DeviceEventEmitter
    : new NativeEventEmitter(GenesysChat);

export default function startChat(
  deploymentId: string,
  domain: string,
  attributs?: { [key: string]: string },
  logging: boolean = false
) {
  try {
    if (!deploymentId) console.error("deploymentId id is required");
    else if (!domain) console.error("domain id is required");
    else GenesysChat.startChat(deploymentId, domain, attributs, logging);
  } catch (err) {
    console.error(err);
  }
}

function setupMessengerTransport(
  deploymentId: string,
  domain: string,
  attributs?: { [key: string]: string },
  logging: boolean = false
) {
  try {
    if (!deploymentId) console.error("deploymentId id is required");
    else if (!domain) console.error("domain id is required");
    else {
      GenesysChat.setupMessengerTransport(
        deploymentId,
        domain,
        attributs,
        logging
      );
    }
  } catch (err) {
    console.error(err);
  }
}

async function getConversation() {
  return await GenesysChat.getConversation();
}

async function sendMessage(
  query: string,
  attributs?: { [key: string]: string }
) {
  try {
    GenesysChat.sendMessage(query, attributs || {});
  } catch (err) {
    console.error("sendMessage", err);
  }
}

async function clearHistory() {
  try {
    await GenesysChat.clearHistory();
  } catch (err) {
    console.error("clearHistory", err);
  }
}

async function disconnectAndCleanup() {
  try {
    await GenesysChat.disconnectAndCleanup();
  } catch (err) {
    console.error("disconnectAndCleanup", err);
  }
}

export {
  genesysChatEventEmitter,
  setupMessengerTransport,
  getConversation,
  sendMessage,
  clearHistory,
  disconnectAndCleanup,
};
