import { Client } from "@stomp/stompjs";
import type { Resposta } from "../interfaces/Resposta";

export const abrirWebSocket = (
  clientId: string,
  aoReceber: (resposta: Resposta) => void,
  aoMudarConexao: (conectado: boolean) => void,
) => {
  const url = new URL("/ws", window.location.href);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";

  const client = new Client({
    brokerURL: url.href,
    connectHeaders: { "client-id": clientId },
    reconnectDelay: 3000,
    connectionTimeout: 8000,
    onConnect: () => {
      client.subscribe("/user/queue/respostas", (message) => {
        try {
          const resposta: Resposta = JSON.parse(message.body);
          if (typeof resposta.correlation_id === "string" &&
              typeof resposta.texto === "string" &&
              (resposta.status === "OK" || resposta.status === "ERRO")) {
            aoReceber(resposta);
          }
        } catch {
          // A consulta HTTP de recuperação continua funcionando.
        }
      });
      aoMudarConexao(true);
    },
    onStompError: () => aoMudarConexao(false),
    onWebSocketClose: () => aoMudarConexao(false),
  });
  client.activate();

  return () => { void client.deactivate(); };
};
