package br.uff.tutoria.comum.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// Payload publicado pelo Worker em respostas.exchange.
// status: "OK" (texto é a resposta da IA) ou "ERRO" (texto é uma mensagem para o aluno).
public record RespostaMensagem(
        @JsonProperty("correlation_id") String correlationId,
        String texto,
        String status) {

    public static RespostaMensagem ok(String correlationId, String texto) {
        return new RespostaMensagem(correlationId, texto, "OK");
    }

    public static RespostaMensagem erro(String correlationId, String texto) {
        return new RespostaMensagem(correlationId, texto, "ERRO");
    }
}