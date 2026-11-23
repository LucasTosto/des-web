package br.uff.tutoria.worker.publisher;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RespostaPublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);
    private final RespostaPublisher publisher = new RespostaPublisher(template);
    private final RespostaMensagem resposta = RespostaMensagem.ok("id-original", "Resposta");

    @Test
    void devePublicarNaRotaDeRespostasComPersistenciaEOIdOriginal() {
        simularConfirmacao(true, false);
        publisher.publicar(resposta);
    }

    @Test
    void deveFalharComAckSeMensagemNaoTiverRota() {
        simularConfirmacao(true, true);
        assertThrows(AmqpException.class, () -> publisher.publicar(resposta));
    }

    @Test
    void deveFalharComNackDoBroker() {
        simularConfirmacao(false, false);
        assertThrows(AmqpException.class, () -> publisher.publicar(resposta));
    }

    private void simularConfirmacao(boolean ack, boolean semRota) {
        doAnswer(invocacao -> {
            MessagePostProcessor processador = invocacao.getArgument(3);
            var mensagem = processador.postProcessMessage(new Message(new byte[0], new MessageProperties()));
            assertEquals("id-original", mensagem.getMessageProperties().getCorrelationId());
            assertEquals(MessageDeliveryMode.PERSISTENT, mensagem.getMessageProperties().getDeliveryMode());

            CorrelationData dados = invocacao.getArgument(4);
            if (semRota) {
                dados.setReturned(new ReturnedMessage(mensagem, 312, "NO_ROUTE",
                    RabbitMQConfig.RESPOSTAS_EXCHANGE, RabbitMQConfig.RESPOSTAS_ROUTING_KEY));
            }
            dados.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(template).convertAndSend(eq(RabbitMQConfig.RESPOSTAS_EXCHANGE),
            eq(RabbitMQConfig.RESPOSTAS_ROUTING_KEY), eq(resposta),
            any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}
