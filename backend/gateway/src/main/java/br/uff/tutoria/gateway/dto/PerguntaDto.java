package br.uff.tutoria.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PerguntaDto(@NotBlank String texto, UUID respostaAnteriorId) {
}
