package br.uff.tutoria.worker.prompt;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import br.uff.tutoria.comum.dto.PerguntaMensagem;
import org.springframework.stereotype.Service;

@Service
public class EnriquecedorDePrompt {

    private enum Tema {
        COMPUTACAO("Computação", "programacao", "algoritmo", "algoritmos", "java", "python",
            "react", "javascript", "banco de dados", "software", "codigo", "estrutura de dados", "api", "sql"),
        MATEMATICA("Matemática", "matematica", "equacao", "equacoes", "algebra", "geometria",
            "probabilidade", "estatistica", "derivada", "integral"),
        CIENCIAS("Ciências naturais", "fisica", "quimica", "biologia", "celula", "reacao quimica",
            "fotossintese", "fotossintetico", "forca gravitacional"),
        HUMANIDADES("Humanidades", "historia", "revolucao francesa", "filosofia", "sociologia",
            "literatura", "gramatica", "poesia"),
        ABERTO("Assunto a identificar");

        final String nome;
        final List<String> sinais;

        Tema(String nome, String... sinais) {
            this.nome = nome;
            this.sinais = List.of(sinais);
        }
    }

    public PromptTutor enriquecer(PerguntaMensagem mensagem) {
        String texto = normalizar(mensagem.texto());
        PerfilTutoria perfil = identificarPerfil(texto);
        Tema tema = identificarTema(texto);

        String orientacaoTema = tema == Tema.ABERTO
            ? "Identifique o assunto pela pergunta, sem assumir uma área se ela for ambígua."
            : "Use " + tema.nome + " como contexto inicial, mas siga o assunto real da pergunta.";

        String instrucao = """
            Você é um tutor acadêmico. Responda em português do Brasil, a menos que o aluno peça outro idioma.
            Adapte o nível ao aluno, sinalize incertezas e não invente fontes.
            %s
            %s
            """.formatted(orientacaoTema, perfil.instrucao).strip();

        return new PromptTutor(mensagem.correlationId(), tema.nome, perfil.name(),
            perfil.temperatura, instrucao, mensagem.texto());
    }

    private PerfilTutoria identificarPerfil(String texto) {
        PerfilTutoria escolhido = PerfilTutoria.PADRAO;
        int melhor = 0;
        for (PerfilTutoria perfil : PerfilTutoria.values()) {
            int pontos = perfil.pontuar(texto);
            if (pontos > melhor) {
                melhor = pontos;
                escolhido = perfil;
            } else if (pontos > 0 && pontos == melhor) {
                escolhido = PerfilTutoria.PADRAO;
            }
        }
        return escolhido;
    }

    private Tema identificarTema(String texto) {
        Tema escolhido = Tema.ABERTO;
        int melhor = 0;
        for (Tema tema : Tema.values()) {
            int pontos = pontuar(texto, tema.sinais);
            if (pontos > melhor) {
                melhor = pontos;
                escolhido = tema;
            } else if (pontos > 0 && pontos == melhor) {
                escolhido = Tema.ABERTO;
            }
        }
        return escolhido;
    }

    private int pontuar(String texto, List<String> sinais) {
        return sinais.stream()
            .filter(sinal -> texto.contains(" " + sinal + " "))
            .mapToInt(sinal -> sinal.contains(" ") ? 2 : 1)
            .sum();
    }

    private String normalizar(String texto) {
        return " " + Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .strip() + " ";
    }
}
