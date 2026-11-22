package br.uff.tutoria.worker.ia;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

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
                .retryOn(RuntimeException.class)
                .build();
    }

    /** Consulta a IA; após esgotar as tentativas, relança a última exceção. */
    public String responder(String instrucao, String pergunta, double temperatura) {
        return retryTemplate.execute(contexto -> {
            try {
                return consultar(instrucao, pergunta, temperatura);
            } catch (RuntimeException e) {
                LOG.warn("Falha ao consultar a IA (tentativa {}/{}): {}",
                        contexto.getRetryCount() + 1, maxTentativas, e.getClass().getSimpleName());
                throw e;
            }
        });
    }

    private String consultar(String instrucao, String pergunta, double temperatura) {
        // Mensagens explícitas (sem template): chaves { } na pergunta não viram placeholder.
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(instrucao), new UserMessage(pergunta)),
                ChatOptions.builder().temperature(temperatura).build());
        String texto = chatModel.call(prompt).getResult().getOutput().getText();
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException("Resposta vazia da IA");
        }
        return texto;
    }
}