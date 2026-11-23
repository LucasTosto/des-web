package br.uff.tutoria.comum.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PerguntaMensagem(
        @JsonProperty("correlation_id") String correlationId,
        String texto,
        List<TurnoConversa> historico) {

    public PerguntaMensagem {
        historico = historico == null ? List.of() : List.copyOf(historico);
    }

    public PerguntaMensagem(String correlationId, String texto) {
        this(correlationId, texto, List.of());
    }
}
