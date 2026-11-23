package br.uff.tutoria.gateway;

import java.util.List;

import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.comum.dto.TurnoConversa;
import br.uff.tutoria.gateway.service.CorrelationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationServiceTest {

    @Test
    void mantemOsTresTurnosMaisRecentesEmOrdem() {
        var sessoes = new CorrelationService();
        String sessao = sessoes.criarSessao();
        String anterior = null;
        for (int i = 1; i <= 4; i++) {
            String id = "turno-" + i;
            sessoes.associar(id, sessao, "Pergunta " + i, anterior);
            sessoes.registrarResposta(RespostaMensagem.ok(id, "Resposta " + i));
            anterior = id;
        }
        assertEquals(List.of(
                new TurnoConversa("Pergunta 2", "Resposta 2"),
                new TurnoConversa("Pergunta 3", "Resposta 3"),
                new TurnoConversa("Pergunta 4", "Resposta 4")),
                sessoes.montarHistorico(anterior, sessao));
        assertEquals(List.of(), sessoes.montarHistorico(null, sessao));
    }

    @Test
    void naoUsaRespostasPendentesOuComErroComoContexto() {
        var sessoes = new CorrelationService();
        String sessao = sessoes.criarSessao();
        sessoes.associar("id", sessao, "Pergunta", null);
        assertThrows(ResponseStatusException.class, () -> sessoes.montarHistorico("id", sessao));
        sessoes.registrarResposta(RespostaMensagem.erro("id", "Erro"));
        assertThrows(ResponseStatusException.class, () -> sessoes.montarHistorico("id", sessao));
    }
}
