import type { Resposta } from "../interfaces/Resposta";

// Compartilhada pelos componentes desta página, mas não com outras abas.
let sessao: Promise<string> | undefined;

export const criarSessao = () => {
  sessao ??= fetch("/api/sessoes", { method: "POST" })
    .then(async (response) => {
      if (!response.ok) throw new Error("Não foi possível abrir uma sessão no Gateway.");
      const { clientId } = await response.json();
      return clientId as string;
    })
    .catch((error: unknown) => {
      sessao = undefined;
      throw error;
    });
  return sessao;
};

export const consultarResposta = async (id: string, clientId: string, signal: AbortSignal) => {
  const response = await fetch(`/api/perguntas/${encodeURIComponent(id)}/resposta`, {
    headers: { "X-Client-Id": clientId },
    cache: "no-store",
    signal,
  });
  return {
    status: response.status,
    resposta: response.status === 200 ? await response.json() as Resposta : null,
  };
};
