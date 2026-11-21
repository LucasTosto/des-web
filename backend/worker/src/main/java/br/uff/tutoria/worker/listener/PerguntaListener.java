package br.uff.tutoria.worker.listener;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.worker.prompt.EnriquecedorDePrompt;
import br.uff.tutoria.worker.prompt.PromptTutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PerguntaListener {

    private static final Logger LOG = LoggerFactory.getLogger(PerguntaListener.class);

    private final EnriquecedorDePrompt enriquecedor;

    public PerguntaListener(EnriquecedorDePrompt enriquecedor) {
        this.enriquecedor = enriquecedor;
    }

    @RabbitListener(queues = RabbitMQConfig.PERGUNTAS_QUEUE)
    public void receber(PerguntaMensagem mensagem) {
        PromptTutor prompt = enriquecedor.enriquecer(mensagem);
        // Nesta etapa apenas demonstramos a seleção do perfil, sem consultar a IA.
        LOG.info("Pergunta preparada: correlation_id={}, tema={}, perfil={}, temperatura={}",
            prompt.correlationId(), prompt.tema(), prompt.perfil(), prompt.temperatura());
    }
}
