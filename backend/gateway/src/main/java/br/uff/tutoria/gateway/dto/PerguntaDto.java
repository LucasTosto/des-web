package br.uff.tutoria.gateway.dto;

import jakarta.validation.constraints.NotBlank;

public record PerguntaDto(@NotBlank String texto) {
}
