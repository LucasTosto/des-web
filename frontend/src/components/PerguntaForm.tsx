import { useState, useEffect } from "react";
import type { FormEvent } from "react";
import type { PerguntaRecebida } from "../interfaces/PerguntaRecebida";
import enviarPergunta from "../util/enviarPergunta";
import { clientId, aoReceberResposta } from "../util/websocket";
import type { Resposta } from "../interfaces/Resposta";

const PerguntaForm = () => {
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [recebida, setRecebida] = useState<PerguntaRecebida | null>(null);
  const [resposta, setResposta] = useState<Resposta | null>(null);

  // Escuta as respostas recebidas pelo WebSocket.
  useEffect(() => { const cancelarInscricao = aoReceberResposta((mensagem) => {
      setResposta(mensagem);});
    return cancelarInscricao;
  }, []);

  const tratarEnvio = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (enviando) return;

    setErro("");
    setRecebida(null);
    const pergunta = texto.trim();
    if (!pergunta) {
      setErro("Informe o texto da pergunta.");
      return;
    }

    setEnviando(true);
    try {
      const confirmacao = await enviarPergunta({ texto: pergunta }, clientId);
      setRecebida(confirmacao);
      setTexto("");
    } catch (error) {
      setErro(error instanceof Error ? error.message : "Não foi possível enviar a pergunta.");
    } finally {
      setEnviando(false);
    }
  };

  // Verifica se a resposta pertence à pergunta atual.
  const respostaAtual = resposta?.correlation_id === recebida?.correlation_id? resposta : null;

  return (
    <form onSubmit={tratarEnvio} aria-busy={enviando} className="space-y-3">
      <label htmlFor="pergunta" className="block font-semibold">Sua pergunta</label>
      <textarea
        id="pergunta"
        value={texto}
        onChange={(event) => setTexto(event.target.value)}
        rows={5}
        required
        disabled={enviando}
        placeholder="Ex.: O que é escopo em programação?"
        className="block w-full rounded-md border border-gray-400 px-3 py-2 disabled:opacity-60"
      />
      <button
        type="submit"
        disabled={enviando || !texto.trim()}
        className="rounded-md bg-green-700 px-4 py-2 font-semibold text-white hover:bg-green-800 disabled:opacity-60"
      >
        {enviando ? "Enviando..." : "Enviar pergunta"}
      </button>
      {erro && <p role="alert" className="rounded-md border border-red-300 bg-red-50 p-3 text-red-800">{erro}</p>}
      {recebida && (
        <div role="status" className="rounded-md border border-green-300 bg-green-50 p-3">
          <p>Pergunta enviada para a fila de processamento. A resposta da IA ainda não é exibida nesta etapa.</p>
          <p className="mt-1 break-all text-sm">Identificador: {recebida.correlation_id}</p>
        </div>
      )}
      {recebida && !respostaAtual && (
        <p className="text-gray-600">
          Aguardando resposta da IA...
        </p>
      )}

      {respostaAtual && (
        <div className={`rounded-md border p-4 ${respostaAtual.status === "OK"? "border-blue-300 bg-blue-50" : "border-red-300 bg-red-50"}`}>
          <h3 className="mb-2 font-semibold">
            {respostaAtual.status === "OK"? "Resposta da IA" : "Erro no processamento"}
          </h3>
          <p className="whitespace-pre-wrap">
            {respostaAtual.texto}
          </p>
        </div>)}
    </form>
  );
};

export default PerguntaForm;
