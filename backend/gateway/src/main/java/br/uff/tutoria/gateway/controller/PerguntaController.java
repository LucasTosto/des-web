package br.uff.tutoria.gateway.controller;

import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import br.uff.tutoria.gateway.service.PerguntaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/perguntas")
public class PerguntaController {

    @Autowired
    private PerguntaService perguntaService;

    // 202 Accepted: a pergunta foi aceita e está na fila, mas ainda não foi respondida.
    // A resposta da IA chega depois, de forma assíncrona (etapas 3 a 5).
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PerguntaRecebidaDto receber(@Valid @RequestBody PerguntaDto pergunta) {
        return perguntaService.receber(pergunta);
    }
}
