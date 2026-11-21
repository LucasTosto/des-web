import type { Pergunta } from "../interfaces/Pergunta";
import type { PerguntaRecebida } from "../interfaces/PerguntaRecebida";

const enviarPergunta = async (pergunta: Pergunta): Promise<PerguntaRecebida> => {
  const response = await fetch("/api/perguntas", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(pergunta),
  });

  if (!response.ok) {
    if (response.status === 400) throw new Error("Informe o texto da pergunta.");
    if (response.status === 503) throw new Error("Não foi possível confirmar o envio. A pergunta pode já estar na fila.");
    throw new Error("Não foi possível enviar a pergunta. Verifique se o Gateway está rodando.");
  }

  return response.json();
};

export default enviarPergunta;
