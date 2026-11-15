package br.uff.tutoria.gateway.controller;

import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import br.uff.tutoria.gateway.service.PerguntaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/perguntas")
public class PerguntaController {

    @Autowired
    private PerguntaService perguntaService;

    @PostMapping
    public PerguntaRecebidaDto receber(@Valid @RequestBody PerguntaDto pergunta) {
        return perguntaService.receber(pergunta);
    }
}
