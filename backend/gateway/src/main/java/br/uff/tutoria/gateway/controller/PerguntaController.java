package br.uff.tutoria.gateway.controller;

import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import br.uff.tutoria.gateway.service.PerguntaService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/perguntas")
public class PerguntaController {

    private final PerguntaService perguntaService;

    public PerguntaController(PerguntaService perguntaService) {
        this.perguntaService = perguntaService;
    }

    // 202 Accepted: pergunta aceita e encaminhada para processamento.
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PerguntaRecebidaDto receber(
            @RequestHeader("X-Client-Id") String clientId,
            @Valid @RequestBody PerguntaDto pergunta) {

        return perguntaService.receber(pergunta, clientId);
    }
}
