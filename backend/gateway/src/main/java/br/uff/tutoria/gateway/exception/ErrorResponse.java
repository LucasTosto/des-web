package br.uff.tutoria.gateway.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    LocalDateTime localDateTime,
    int errorCode,
    String error,
    String metodo,
    String requestUri,
    String message) {
}
