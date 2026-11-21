package br.uff.tutoria.gateway.service;

import java.util.UUID;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PerguntaService {

    // Tempo máximo, em milissegundos, para o RabbitMQ confirmar que recebeu a mensagem.
    private static final long TEMPO_CONFIRMACAO = 5000;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public PerguntaRecebidaDto receber(PerguntaDto pergunta) {
        String correlationId = UUID.randomUUID().toString();
        PerguntaMensagem mensagem = new PerguntaMensagem(correlationId, pergunta.texto());

        // invoke() executa a publicação e a espera da confirmação no mesmo canal AMQP.
        // Com spring.rabbitmq.publisher-confirm-type=simple, o RabbitMQ confirma (ack) a
        // mensagem depois de entregá-la à fila durável. Se ele recusar (nack) ou não responder
        // a tempo, waitForConfirmsOrDie() lança AmqpException e o Gateway não devolve o
        // correlation_id de uma pergunta que não foi guardada (veja GlobalExceptionHandler).
        rabbitTemplate.invoke(operacoes -> {
            operacoes.convertAndSend(
                RabbitMQConfig.PERGUNTAS_EXCHANGE,
                RabbitMQConfig.PERGUNTAS_ROUTING_KEY,
                mensagem,
                message -> {
                    // Mensagem persistente: gravada em disco junto com a fila durável.
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // Também no cabeçalho AMQP, para rastrear a pergunta no painel do RabbitMQ.
                    message.getMessageProperties().setCorrelationId(correlationId);
                    return message;
                });
            operacoes.waitForConfirmsOrDie(TEMPO_CONFIRMACAO);
            return null;
        });

        return new PerguntaRecebidaDto(correlationId);
    }
}
