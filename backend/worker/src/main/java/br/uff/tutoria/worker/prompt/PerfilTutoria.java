package br.uff.tutoria.worker.prompt;

import java.util.regex.Pattern;

enum PerfilTutoria {

    ANALITICO(0.0,
        "Priorize precisão. Compare critérios, aponte hipóteses e justifique as conclusões. Não invente dados.",
        "qual (?:e )?a diferenca|quais (?:sao )?as diferencas|(?:vantagens e desvantagens|pros e contras)|"
            + "em que .{1,80} (?:diferem|se diferenciam)|pode provar|por que",
        "analise|analisar|compare|comparar|avalie|avaliar|demonstre|demonstrar|prove|calcule|justifique|critique"),
    CRIATIVO(0.8,
        "Explore alternativas variadas. Diferencie sugestões de fatos e proponha caminhos para o aluno desenvolver.",
        "(?:de|dar|mande|manda|traga|sugira|sugerir|gere|proponha)(?: me)? (?:algumas |umas )?ideias|"
            + "ideias (?:para|de)|o que eu poderia criar|(?:faca|fazer) um brainstorm|"
            + "proponha alternativas|pense em possibilidades",
        "brainstorm|sugira|sugerir|invente|inventar|imagine|crie|ideacao"),
    PRATICO(0.2,
        "Dê passos acionáveis, um exemplo curto e uma maneira de verificar se funcionou.",
        "como (?:eu |se )?(?:posso |consigo |devo )?(?:fazer|configurar|implementar|instalar|usar|resolver|montar|criar|corrigir)|"
            + "passo a passo|por onde (?:eu )?comeco|quais (?:sao )?os passos|me ajuda a (?:configurar|resolver|implementar)",
        "implemente|implementar|depure|depurar|corrija|corrigir|configure|configurar|instale|tutorial"),
    DIDATICO(0.4,
        "Comece com uma definição simples, dê um exemplo concreto e esclareça uma dúvida comum.",
        "o que (?:e|sao|significa|quer dizer)|qual (?:e )?o significado|"
            + "como (?:isso )?funciona|nao entendi|em palavras simples|conceito de|"
            + "me (?:explica|explique|ensina|ensine)|significado de",
        "explique|explica|explicar|defina|definir|conceitue|resuma"),
    PADRAO(0.2,
        "Responda de forma clara e proporcional à dúvida. Se faltar contexto essencial, peça esclarecimento.",
        "", "");

    private static final Pattern NEGACAO = Pattern.compile(
        "\\bnao (?:quero (?:que voce )?|preciso de |e para )?$");

    final double temperatura;
    final String instrucao;
    private final Pattern expressoes;
    private final Pattern palavras;

    PerfilTutoria(double temperatura, String instrucao, String expressoes, String palavras) {
        this.temperatura = temperatura;
        this.instrucao = instrucao;
        this.expressoes = compilar(expressoes);
        this.palavras = compilar(palavras);
    }

    int pontuar(String texto) {
        if (encontrar(expressoes, texto)) return 3;
        return encontrar(palavras, texto) ? 2 : 0;
    }

    private static Pattern compilar(String sinais) {
        return sinais.isEmpty() ? null : Pattern.compile("\\b(?:" + sinais + ")\\b");
    }

    private static boolean encontrar(Pattern padrao, String texto) {
        if (padrao == null) return false;
        var ocorrencias = padrao.matcher(texto);
        while (ocorrencias.find()) {
            if (!NEGACAO.matcher(texto.substring(0, ocorrencias.start())).find()) return true;
        }
        return false;
    }
}
