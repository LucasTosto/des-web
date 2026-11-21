package br.uff.tutoria.worker.prompt;

import br.uff.tutoria.comum.dto.PerguntaMensagem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnriquecedorDePromptTest {

    private final EnriquecedorDePrompt enriquecedor = new EnriquecedorDePrompt();

    @ParameterizedTest
    @CsvSource({
        "ANÁLISE as vantagens e desvantagens do React, Computação, ANALITICO, 0.0",
        "Me dê ideias para um projeto de física, Ciências naturais, CRIATIVO, 0.8",
        "Como implementar um algoritmo?, Computação, PRATICO, 0.2",
        "O que é uma equação?, Matemática, DIDATICO, 0.4",
        "O que é um algoritmo?, Computação, DIDATICO, 0.4",
        "Subjava?, Assunto a identificar, PADRAO, 0.2",
        "Escopo?, Assunto a identificar, PADRAO, 0.2",
        "Compare literatura e biologia, Assunto a identificar, ANALITICO, 0.0",
        "Explique JavaScript, Computação, DIDATICO, 0.4",
        "Qual a diferença entre Java e Python?, Computação, ANALITICO, 0.0",
        "Você poderia avaliar este algoritmo?, Computação, ANALITICO, 0.0",
        "Consegue me dar umas ideias para estudar biologia?, Ciências naturais, CRIATIVO, 0.8",
        "O que eu poderia criar para a feira de química?, Ciências naturais, CRIATIVO, 0.8",
        "Como eu consigo configurar o React?, Computação, PRATICO, 0.2",
        "Por onde eu começo para montar uma API?, Computação, PRATICO, 0.2",
        "Me explica em palavras simples o que significa integral?, Matemática, DIDATICO, 0.4",
        "Você pode definir fotossíntese?, Ciências naturais, DIDATICO, 0.4",
        "Tenho uma dúvida sobre Python, Computação, PADRAO, 0.2",
        "Não quero analisar esse algoritmo, Computação, PADRAO, 0.2",
        "Explique e compare dois algoritmos, Computação, PADRAO, 0.2"
    })
    void deveEscolherTemaPerfilETemperatura(String pergunta, String tema, String perfil, double temperatura) {
        PromptTutor prompt = enriquecedor.enriquecer(new PerguntaMensagem("pergunta-1", pergunta));

        assertEquals("pergunta-1", prompt.correlationId());
        assertEquals(tema, prompt.tema());
        assertEquals(perfil, prompt.perfil());
        assertEquals(temperatura, prompt.temperatura());
        assertEquals(pergunta, prompt.pergunta());
        assertFalse(prompt.instrucaoSistema().contains(pergunta));
    }

    @Test
    void deveUsarInstrucoesDiferentesParaPedidosDoMesmoAssunto() {
        PromptTutor analitico = enriquecedor.enriquecer(new PerguntaMensagem("1", "Compare algoritmos"));
        PromptTutor criativo = enriquecedor.enriquecer(new PerguntaMensagem("2", "Sugira ideias de algoritmos"));

        assertEquals(analitico.tema(), criativo.tema());
        assertTrue(analitico.instrucaoSistema().contains("Priorize precisão"));
        assertTrue(criativo.instrucaoSistema().contains("Explore alternativas"));
    }
}
