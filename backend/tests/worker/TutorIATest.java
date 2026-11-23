package br.uff.tutoria.worker.ia;

import java.time.Duration;
import java.util.List;

import br.uff.tutoria.comum.dto.TurnoConversa;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorIATest {

    private final ChatModel modelo = mock(ChatModel.class);
    private final TutorIA tutor = new TutorIA(modelo, 3, Duration.ofMillis(1));

    @Test
    void deveEnviarPerguntaLiteralComInstrucaoSeparadaETemperatura() {
        when(modelo.call(any(Prompt.class))).thenReturn(resposta("Resposta"));

        assertEquals("Resposta", tutor.responder("Instruções do tutor", "Explique {x}", 0.8, List.of()));

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(modelo).call(captor.capture());
        var prompt = captor.getValue();
        assertEquals(0.8, prompt.getOptions().getTemperature());
        assertEquals("Instruções do tutor", assertInstanceOf(SystemMessage.class, prompt.getInstructions().get(0)).getText());
        assertEquals("Explique {x}", assertInstanceOf(UserMessage.class, prompt.getInstructions().get(1)).getText());
    }

    @Test
    void deveRepetirFalhaTemporariaERespostaVazia() {
        when(modelo.call(any(Prompt.class)))
            .thenThrow(new TransientAiException("429"))
            .thenReturn(resposta(" "), resposta("Recuperou"));

        assertEquals("Recuperou", tutor.responder("Tutor", "Pergunta", 0.0, List.of()));
        verify(modelo, times(3)).call(any(Prompt.class));
    }

    @Test
    void naoDeveRepetirErroPermanente() {
        when(modelo.call(any(Prompt.class))).thenThrow(new IllegalArgumentException("Configuração inválida"));

        assertThrows(IllegalArgumentException.class, () -> tutor.responder("Tutor", "Pergunta", 0.2, List.of()));
        verify(modelo).call(any(Prompt.class));
    }

    @Test
    void deveEnviarAConversaAntesDaNovaPergunta() {
        when(modelo.call(any(Prompt.class))).thenReturn(resposta("Outro exemplo"));
        tutor.responder("Instruções", "Pode dar outro exemplo?", 0.2,
                List.of(new TurnoConversa("O que é um algoritmo?", "Uma receita é um exemplo.")));

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(modelo).call(captor.capture());
        var mensagens = captor.getValue().getInstructions();
        assertEquals(4, mensagens.size());
        assertEquals("O que é um algoritmo?", assertInstanceOf(UserMessage.class, mensagens.get(1)).getText());
        assertEquals("Uma receita é um exemplo.", assertInstanceOf(AssistantMessage.class, mensagens.get(2)).getText());
        assertEquals("Pode dar outro exemplo?", assertInstanceOf(UserMessage.class, mensagens.get(3)).getText());
    }

    private ChatResponse resposta(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }
}
