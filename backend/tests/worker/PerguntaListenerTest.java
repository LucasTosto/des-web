package br.uff.tutoria.worker.listener;

import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.worker.ia.TutorIA;
import br.uff.tutoria.worker.prompt.EnriquecedorDePrompt;
import br.uff.tutoria.worker.publisher.RespostaPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerguntaListenerTest {

    private final TutorIA tutor = mock(TutorIA.class);
    private final RespostaPublisher publisher = mock(RespostaPublisher.class);
    private final PerguntaListener listener = new PerguntaListener(new EnriquecedorDePrompt(), tutor, publisher);
    private final PerguntaMensagem pergunta = new PerguntaMensagem("id-original", "O que é um algoritmo?");

    @Test
    void devePublicarOResultadoComOMesmoIdDaPergunta() {
        when(tutor.responder(anyString(), eq(pergunta.texto()), eq(0.4))).thenReturn("Resposta");
        listener.receber(pergunta);
        verify(publisher).publicar(RespostaMensagem.ok("id-original", "Resposta"));
    }

    @Test
    void devePublicarUmaRespostaDeErroQuandoATutoriaFalha() {
        when(tutor.responder(anyString(), eq(pergunta.texto()), eq(0.4)))
            .thenThrow(new TransientAiException("Indisponível"));
        listener.receber(pergunta);
        verify(publisher).publicar(RespostaMensagem.erro("id-original",
            "Não foi possível gerar a resposta agora. Tente novamente em instantes."));
    }
}
