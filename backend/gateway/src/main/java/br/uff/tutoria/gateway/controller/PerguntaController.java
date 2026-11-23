package br.uff.tutoria.gateway.controller;

import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.gateway.service.CorrelationService;
import br.uff.tutoria.gateway.service.PerguntaService;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/perguntas")
public class PerguntaController {

    private final PerguntaService perguntaService;
    private final CorrelationService sessoes;

    public PerguntaController(PerguntaService perguntaService, CorrelationService sessoes) {
        this.perguntaService = perguntaService;
        this.sessoes = sessoes;
    }

    // 202 Accepted: pergunta aceita e encaminhada para processamento.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PerguntaRecebidaDto receber(
            @RequestHeader("X-Client-Id") String clientId,
            @Valid @RequestBody PerguntaDto pergunta) {

        validarSessao(clientId);
        return perguntaService.receber(pergunta, clientId);
    }

    @GetMapping("/{correlationId}/resposta")
    public ResponseEntity<RespostaMensagem> consultarResposta(
            @RequestHeader("X-Client-Id") String clientId, @PathVariable String correlationId) {
        validarSessao(clientId);
        var entrega = sessoes.buscar(correlationId, clientId);
        if (entrega == null) return ResponseEntity.notFound().build();
        if (entrega.resposta() == null) return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(entrega.resposta());
    }

    private void validarSessao(String clientId) {
        if (!sessoes.sessaoValida(clientId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida ou expirada.");
        }
    }
}
