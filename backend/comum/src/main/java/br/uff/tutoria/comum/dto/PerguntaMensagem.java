package br.uff.tutoria.comum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Payload publicado pelo Gateway em perguntas.queue e consumido pelo Worker.
// O Jackson converte este record em {"correlation_id": "...", "texto": "..."}.
public record PerguntaMensagem(@JsonProperty("correlation_id") String correlationId, String texto) {
}
