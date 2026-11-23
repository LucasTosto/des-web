import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { Resposta } from "../interfaces/Resposta";
import enviarPergunta from "../util/enviarPergunta";
import { consultarResposta, criarSessao } from "../util/sessao";
import { abrirWebSocket } from "../util/websocket";

interface Envio {
  id: string;
  pergunta: string;
  continuacaoDe?: string;
}

const PerguntaForm = () => {
  const [texto, setTexto] = useState("");
  const [contexto, setContexto] = useState<Envio>();
  const campoPergunta = useRef<HTMLTextAreaElement>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");
  const [clientId, setClientId] = useState<string>();
  const [conectado, setConectado] = useState(false);
  const [tentativa, setTentativa] = useState(0);
  const [sessaoExpirada, setSessaoExpirada] = useState(false);
  const [envios, setEnvios] = useState<Envio[]>([]);
  const [respostas, setRespostas] = useState<Record<string, Resposta>>({});
  const [indisponiveis, setIndisponiveis] = useState<Record<string, boolean>>({});

  const registrarResposta = useCallback((resposta: Resposta) => {
    setRespostas((anteriores) =>
      anteriores[resposta.correlation_id]
        ? anteriores
        : { ...anteriores, [resposta.correlation_id]: resposta },
    );
  }, []);

  useEffect(() => {
    let ativo = true;
    let fechar: (() => void) | undefined;

    void criarSessao().then((id) => {
      if (!ativo) return;
      setClientId(id);
      fechar = abrirWebSocket(id, registrarResposta, (status) => {
        if (ativo) setConectado(status);
      });
    }).catch(() => {
      if (ativo) setErro("Não foi possível abrir uma sessão. Verifique se o Gateway está rodando.");
    });
    return () => {
      ativo = false;
      fechar?.();
    };
  }, [tentativa, registrarResposta]);

  useEffect(() => {
    const pendentes = envios.filter(({ id }) => !respostas[id] && !indisponiveis[id]);
    if (!clientId || sessaoExpirada || pendentes.length === 0) return;

    const controller = new AbortController();
    let consultando = false;
    // O broker STOMP simples não repete notificações perdidas. HTTP cobre a reconexão e a corrida da inscrição.
    const timer = window.setInterval(async () => {
      if (consultando) return;
      consultando = true;
      try {
        await Promise.all(pendentes.map(async ({ id }) => {
          try {
            const { status, resposta } = await consultarResposta(id, clientId, controller.signal);
            if (controller.signal.aborted) return;
            if (resposta) registrarResposta(resposta);
            else if (status === 401) setSessaoExpirada(true);
            else if (status === 404) setIndisponiveis((atuais) => ({ ...atuais, [id]: true }));
          } catch {
            // Uma interrupção de rede não invalida a pergunta. Tente novamente no próximo intervalo.
          }
        }));
      } finally {
        consultando = false;
      }
    }, 4000);
    return () => {
      window.clearInterval(timer);
      controller.abort();
    };
  }, [clientId, envios, indisponiveis, registrarResposta, respostas, sessaoExpirada]);

  const tratarEnvio = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const pergunta = texto.trim();
    if (!pergunta || enviando || !clientId || !conectado || sessaoExpirada) return;

    setErro("");
    setEnviando(true);
    try {
      const confirmacao = await enviarPergunta({ texto: pergunta, respostaAnteriorId: contexto?.id }, clientId);
      setEnvios((atuais) => [{ id: confirmacao.correlation_id, pergunta, continuacaoDe: contexto?.pergunta }, ...atuais]);
      setTexto("");
      setContexto(undefined);
    } catch (error) {
      setErro(error instanceof Error ? error.message : "Não foi possível enviar a pergunta.");
    } finally {
      setEnviando(false);
    }
  };

  return (
    <form onSubmit={tratarEnvio} aria-busy={enviando} className="space-y-3">
      {contexto && (
        <div className="flex flex-wrap items-center gap-2 rounded-md bg-gray-100 p-3 text-sm">
          <p className="min-w-0 break-words">Continuando: <strong>{contexto.pergunta}</strong></p>
          <button type="button" disabled={enviando} className="underline" onClick={() => setContexto(undefined)}>
            Enviar sem contexto
          </button>
        </div>
      )}
      <label htmlFor="pergunta" className="block font-semibold">Sua pergunta</label>
      <textarea
        id="pergunta"
        ref={campoPergunta}
        value={texto}
        onChange={(event) => setTexto(event.target.value)}
        rows={5}
        required
        disabled={enviando}
        placeholder={contexto ? "Ex.: Pode dar outro exemplo?" : "Ex.: O que é escopo em programação?"}
        className="block w-full rounded-md border border-gray-400 px-3 py-2 disabled:opacity-60"
      />
      <p role="status" className="text-sm text-gray-600">
        {sessaoExpirada ? "Sessão expirada. Recarregue a página."
          : conectado ? "Conectado para receber respostas."
            : "Aguardando conexão com o Gateway..."}
      </p>
      <button
        type="submit"
        disabled={enviando || !texto.trim() || !conectado || sessaoExpirada}
        className="rounded-md bg-green-700 px-4 py-2 font-semibold text-white hover:bg-green-800 disabled:opacity-60"
      >
        {enviando ? "Enviando..." : "Enviar pergunta"}
      </button>
      {erro && <p role="alert" className="rounded-md border border-red-300 bg-red-50 p-3 text-red-800">{erro}</p>}
      {!clientId && erro && (
        <button type="button" className="underline" onClick={() => { setErro(""); setTentativa((n) => n + 1); }}>
          Tentar conectar novamente
        </button>
      )}
      {envios.map((envio) => {
        const { id, pergunta, continuacaoDe } = envio;
        const resposta = respostas[id];
        return (
          <article key={id} className="space-y-2 rounded-md border border-gray-300 p-4">
            <h2 className="font-semibold">{pergunta}</h2>
            {continuacaoDe && <p className="break-words text-sm text-gray-600">Continuação de: {continuacaoDe}</p>}
            <p className="break-all text-sm">Identificador: {id}</p>
            {resposta ? (
              <div role="status" className={resposta.status === "OK" ? "text-gray-900" : "text-red-800"}>
                <h3 className="font-semibold">{resposta.status === "OK" ? "Resposta da IA" : "Erro no processamento"}</h3>
                <p className="whitespace-pre-wrap">{resposta.texto}</p>
                {resposta.status === "OK" && (
                  <button type="button" disabled={enviando || sessaoExpirada}
                    className="mt-2 font-semibold text-green-700 underline"
                    onClick={() => { setContexto(envio); campoPergunta.current?.focus(); }}>
                    Continuar daqui
                  </button>
                )}
              </div>
            ) : (
              <p role="status" className="text-gray-600">
                {sessaoExpirada || indisponiveis[id] ? "Não foi possível recuperar esta resposta."
                  : "Pergunta enviada. Aguardando resposta da IA..."}
              </p>
            )}
          </article>
        );
      })}
    </form>
  );
};

export default PerguntaForm;
