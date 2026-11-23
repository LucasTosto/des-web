package br.uff.tutoria.gateway.controller;

import java.util.Map;

import br.uff.tutoria.gateway.service.CorrelationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessaoController {

    private final CorrelationService sessoes;

    public SessaoController(CorrelationService sessoes) {
        this.sessoes = sessoes;
    }

    @PostMapping("/sessoes")
    public ResponseEntity<Map<String, String>> criar() {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(Map.of("clientId", sessoes.criarSessao()));
    }
}
