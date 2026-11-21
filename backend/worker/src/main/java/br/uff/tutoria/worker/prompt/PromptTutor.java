package br.uff.tutoria.worker.prompt;

public record PromptTutor(
    String correlationId,
    String tema,
    String perfil,
    double temperatura,
    String instrucaoSistema,
    String pergunta
) {
}
