package br.uff.tutoria.gateway.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.uff.tutoria.comum.dto.RespostaMensagem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CorrelationService {

    private static final Duration DURACAO_SESSAO = Duration.ofHours(1);
    private final Map<String, Instant> sessoes = new ConcurrentHashMap<>();
    private final Map<String, Entrega> entregas = new ConcurrentHashMap<>();

    public record Entrega(String cliente, Instant expiraEm, RespostaMensagem resposta) {
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

    public void associar(String correlationId, String clientId) {
        entregas.put(correlationId, new Entrega(clientId, sessoes.get(clientId), null));
    }

    public Entrega buscar(String correlationId, String clientId) {
        Entrega entrega = entregas.get(correlationId);
        return entrega != null && entrega.cliente().equals(clientId) && sessaoValida(clientId) ? entrega : null;
    }

    public Entrega registrarResposta(RespostaMensagem resposta) {
        return entregas.computeIfPresent(resposta.correlationId(), (id, atual) -> {
            if (!sessaoValida(atual.cliente())) return null;
            if (atual.resposta() != null) return atual;
            return new Entrega(atual.cliente(), atual.expiraEm(), resposta);
        });
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
