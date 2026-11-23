package br.uff.tutoria.gateway.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.comum.dto.TurnoConversa;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CorrelationService {

    private static final Duration DURACAO_SESSAO = Duration.ofHours(1);
    private static final int MAX_TURNOS = 3;
    private static final int MAX_CARACTERES_PERGUNTA = 700;
    private static final int MAX_CARACTERES_RESPOSTA = 1400;
    private final Map<String, Instant> sessoes = new ConcurrentHashMap<>();
    private final Map<String, Entrega> entregas = new ConcurrentHashMap<>();

    public record Entrega(String cliente, Instant expiraEm, String pergunta, String respostaAnteriorId,
                          RespostaMensagem resposta) {
    }

    public String criarSessao() {
        String token = UUID.randomUUID().toString();
        sessoes.put(token, Instant.now().plus(DURACAO_SESSAO));
        return token;
    }

    public boolean sessaoValida(String token) {
        if (token == null) return false;
        Instant expiracao = sessoes.get(token);
        return expiracao != null && expiracao.isAfter(Instant.now());
    }

    public void associar(String correlationId, String clientId, String pergunta, String respostaAnteriorId) {
        entregas.put(correlationId, new Entrega(clientId, sessoes.get(clientId), pergunta, respostaAnteriorId, null));
    }

    public Entrega buscar(String correlationId, String clientId) {
        Entrega entrega = entregas.get(correlationId);
        return entrega != null && entrega.cliente().equals(clientId) && sessaoValida(clientId) ? entrega : null;
    }

    public Entrega registrarResposta(RespostaMensagem resposta) {
        return entregas.computeIfPresent(resposta.correlationId(), (id, atual) -> {
            if (!sessaoValida(atual.cliente())) return null;
            if (atual.resposta() != null) return atual;
            return new Entrega(atual.cliente(), atual.expiraEm(), atual.pergunta(), atual.respostaAnteriorId(), resposta);
        });
    }

    public List<TurnoConversa> montarHistorico(String respostaAnteriorId, String clientId) {
        List<TurnoConversa> turnos = new ArrayList<>();
        String id = respostaAnteriorId;
        while (id != null && turnos.size() < MAX_TURNOS) {
            Entrega entrega = buscar(id, clientId);
            if (entrega == null || entrega.resposta() == null || !"OK".equals(entrega.resposta().status())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resposta anterior indisponível.");
            }
            turnos.addFirst(new TurnoConversa(
                    limitar(entrega.pergunta(), MAX_CARACTERES_PERGUNTA),
                    limitar(entrega.resposta().texto(), MAX_CARACTERES_RESPOSTA)));
            id = entrega.respostaAnteriorId();
        }
        return List.copyOf(turnos);
    }

    private String limitar(String texto, int limite) {
        if (texto.codePointCount(0, texto.length()) <= limite) return texto;
        return texto.substring(0, texto.offsetByCodePoints(0, limite)) + "… [texto abreviado]";
    }

    public void remover(String correlationId) {
        entregas.remove(correlationId);
    }

    @Scheduled(fixedDelay = 60_000)
    void limparExpiradas() {
        Instant agora = Instant.now();
        sessoes.values().removeIf(expiracao -> !expiracao.isAfter(agora));
        entregas.values().removeIf(entrega -> entrega.expiraEm() == null || !entrega.expiraEm().isAfter(agora));
    }
}
