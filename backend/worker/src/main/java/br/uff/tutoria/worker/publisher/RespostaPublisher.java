package br.uff.tutoria.worker.publisher;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RespostaPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RespostaPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(RespostaMensagem resposta) {
        // ID de publicação distinto a cada tentativa. O ID da pergunta permanece no payload e no AMQP.
        CorrelationData publicacao = new CorrelationData();
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESPOSTAS_EXCHANGE,
                RabbitMQConfig.RESPOSTAS_ROUTING_KEY,
                resposta,
                mensagem -> {
                    mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    mensagem.getMessageProperties().setCorrelationId(resposta.correlationId());
                    return mensagem;
                },
                publicacao);

        try {
            var confirmacao = publicacao.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmacao.isAck() || publicacao.getReturned() != null) {
                throw new AmqpException("Resposta rejeitada ou sem fila de destino.");
            }
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("Não foi possível confirmar a publicação da resposta.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Espera pela confirmação da resposta interrompida.", e);
        }
    }
}
