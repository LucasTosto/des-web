package br.uff.tutoria.worker.ia;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import br.uff.tutoria.comum.dto.TurnoConversa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

@Service
public class TutorIA {

    private static final Logger LOG = LoggerFactory.getLogger(TutorIA.class);

    private final ChatModel chatModel;
    private final int maxTentativas;
    private final RetryTemplate retryTemplate;

    public TutorIA(ChatModel chatModel,
                   @Value("${tutoria.ia.max-tentativas:3}") int maxTentativas,
                   @Value("${tutoria.ia.intervalo-inicial:2s}") Duration intervaloInicial) {
        this.chatModel = chatModel;
        this.maxTentativas = maxTentativas;
        // Não é um @Bean: o Spring AI já registra o seu próprio RetryTemplate.
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(maxTentativas)
                .exponentialBackoff(intervaloInicial.toMillis(), 2.0, 10_000)
                .retryOn(TransientAiException.class)
                .retryOn(ResourceAccessException.class)
                .retryOn(RespostaVaziaException.class)
                .build();
    }

    /** Consulta a IA; após esgotar as tentativas, relança a última exceção. */
    public String responder(String instrucao, String pergunta, double temperatura,
                            List<TurnoConversa> historico) {
        return retryTemplate.execute(contexto -> {
            try {
                return consultar(instrucao, pergunta, temperatura, historico);
            } catch (RuntimeException e) {
                LOG.warn("Falha ao consultar a IA (tentativa {}/{}): {}",
                        contexto.getRetryCount() + 1, maxTentativas, e.getClass().getSimpleName());
                throw e;
            }
        });
    }

    private String consultar(String instrucao, String pergunta, double temperatura,
                            List<TurnoConversa> historico) {
        // Mensagens explícitas (sem template): chaves { } na pergunta não viram placeholder.
        List<Message> mensagens = new ArrayList<>();
        mensagens.add(new SystemMessage(instrucao));
        for (TurnoConversa turno : historico) {
            mensagens.add(new UserMessage(turno.pergunta()));
            mensagens.add(new AssistantMessage(turno.resposta()));
        }
        mensagens.add(new UserMessage(pergunta));
        Prompt prompt = new Prompt(mensagens,
                ChatOptions.builder().temperature(temperatura).build());
        var resposta = chatModel.call(prompt);
        var resultado = resposta == null ? null : resposta.getResult();
        String texto = resultado == null || resultado.getOutput() == null
                ? null : resultado.getOutput().getText();
        if (texto == null || texto.isBlank()) {
            throw new RespostaVaziaException();
        }
        return texto;
    }

    private static class RespostaVaziaException extends RuntimeException {
        RespostaVaziaException() {
            super("Resposta vazia da IA");
        }
    }
}