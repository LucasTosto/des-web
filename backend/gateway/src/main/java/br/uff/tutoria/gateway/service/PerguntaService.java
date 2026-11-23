
package br.uff.tutoria.gateway.service;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PerguntaService {

    private static final long TEMPO_CONFIRMACAO = 5;

    private final RabbitTemplate rabbitTemplate;
    private final CorrelationService correlationService;

    public PerguntaService(
            RabbitTemplate rabbitTemplate,
            CorrelationService correlationService) {

        this.rabbitTemplate = rabbitTemplate;
        this.correlationService = correlationService;
    }

    public PerguntaRecebidaDto receber(
            PerguntaDto pergunta,
            String clientId) {

        String correlationId = UUID.randomUUID().toString();

        String respostaAnteriorId = pergunta.respostaAnteriorId() == null
                ? null : pergunta.respostaAnteriorId().toString();
        var historico = correlationService.montarHistorico(respostaAnteriorId, clientId);
        PerguntaMensagem mensagem = new PerguntaMensagem(correlationId, pergunta.texto(), historico);

        CorrelationData publicacao =
                new CorrelationData(correlationId);

        // Associa a pergunta ao cliente antes de publicá-la.
        correlationService.associar(correlationId, clientId, pergunta.texto(), respostaAnteriorId);

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PERGUNTAS_EXCHANGE,
                    RabbitMQConfig.PERGUNTAS_ROUTING_KEY,
                    mensagem,
                    message -> {
                        message.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);

                        message.getMessageProperties()
                                .setCorrelationId(correlationId);

                        return message;
                    },
                    publicacao
            );

            confirmarPublicacao(publicacao);

            return new PerguntaRecebidaDto(correlationId);

        } catch (RuntimeException ex) {

            // Remove a associação se a publicação falhar.
            correlationService.remover(correlationId);

            throw ex;
        }
    }

    private void confirmarPublicacao(CorrelationData publicacao) {
        try {
            var confirmacao = publicacao.getFuture().get(
                    TEMPO_CONFIRMACAO,
                    TimeUnit.SECONDS
            );

            // O Spring AMQP disponibiliza a devolução antes
            // de completar a confirmação.
            if (publicacao.getReturned() != null || !confirmacao.isAck()) {
                throw new AmqpException(
                        "Publicação rejeitada ou sem fila associada."
                );
            }

        } catch (TimeoutException | ExecutionException ex) {
            throw new AmqpException(
                    "Não foi possível confirmar a publicação.", ex
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new AmqpException(
                    "Espera pela confirmação interrompida.", ex
            );
        }
    }
}
