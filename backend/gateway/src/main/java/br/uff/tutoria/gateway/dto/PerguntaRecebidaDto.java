package br.uff.tutoria.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PerguntaRecebidaDto(@JsonProperty("correlation_id") String correlationId) {
}
