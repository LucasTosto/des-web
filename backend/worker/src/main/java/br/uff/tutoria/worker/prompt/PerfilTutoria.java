package br.uff.tutoria.worker.prompt;

import java.util.List;

enum PerfilTutoria {

    ANALITICO(0.0,
        "Priorize precisão. Compare critérios, aponte hipóteses e justifique as conclusões. Não invente dados.",
        List.of("analise", "compare", "avalie", "demonstre", "prove", "calcule",
            "justifique", "vantagens e desvantagens", "por que")),
    CRIATIVO(0.8,
        "Explore alternativas variadas. Diferencie sugestões de fatos e proponha caminhos para o aluno desenvolver.",
        List.of("brainstorm", "me de ideias", "ideias", "sugira", "invente", "crie",
            "imagine", "alternativas criativas")),
    PRATICO(0.2,
        "Dê passos acionáveis, um exemplo curto e uma maneira de verificar se funcionou.",
        List.of("como fazer", "como configurar", "como implementar", "como usar", "como resolver",
            "passo a passo", "implemente", "depure", "corrija")),
    DIDATICO(0.4,
        "Comece com uma definição simples, dê um exemplo concreto e esclareça uma dúvida comum.",
        List.of("o que e", "explique", "defina", "conceito de", "como funciona", "nao entendi")),
    PADRAO(0.4,
        "Responda de forma clara e proporcional à dúvida. Se faltar contexto essencial, peça esclarecimento.",
        List.of());

    final double temperatura;
    final String instrucao;
    final List<String> sinais;

    PerfilTutoria(double temperatura, String instrucao, List<String> sinais) {
        this.temperatura = temperatura;
        this.instrucao = instrucao;
        this.sinais = sinais;
    }
}
