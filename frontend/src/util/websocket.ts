
import { Client } from "@stomp/stompjs";
import type { Resposta } from "../interfaces/Resposta";

export const clientId = crypto.randomUUID();

const listeners = new Set<(resposta: Resposta) => void>();

export const aoReceberResposta = (
  callback: (resposta: Resposta) => void
) => {
  listeners.add(callback);

  // Retorna uma função para cancelar a inscrição do componente.
  return () => {
    listeners.delete(callback);
  };
};

export const stompClient = new Client({
  brokerURL: "ws://localhost:8080/ws",

  connectHeaders: {
    "client-id": clientId,
  },

  reconnectDelay: 5000,

  onConnect: () => {
    console.log("Conectado ao Gateway!");

    stompClient.subscribe("/user/queue/respostas", (message) => {
      try {
        const resposta: Resposta = JSON.parse(message.body);

        console.log("Resposta recebida:", resposta);

        listeners.forEach((callback) => callback(resposta));
      } catch (error) {
        console.error("Erro ao interpretar a resposta:", error);
      }
    });
  },

  onStompError: (frame) => {
    console.error("Erro STOMP:", frame.headers["message"]);
  },

  onWebSocketError: (error) => {
    console.error("Erro na conexão WebSocket:", error);
  },
});