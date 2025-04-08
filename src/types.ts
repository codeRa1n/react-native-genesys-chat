export interface Message {
  from: "human" | "companion" | "bot" | "unknown" | "values";
  text: string;
  image?: string;
  type?: string;
}
