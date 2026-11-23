package br.uff.tutoria.gateway.listener;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.gateway.service.CorrelationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RespostaListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final CorrelationService correlationService;

    public RespostaListener(
            SimpMessagingTemplate messagingTemplate,
            CorrelationService correlationService) {

        this.messagingTemplate = messagingTemplate;
        this.correlationService = correlationService;
    }

    @RabbitListener(queues = "respostas.queue")
    public void receber(RespostaMensagem mensagem) {

        String clientId =
                correlationService.buscarCliente(
                        mensagem.correlationId()
                );

        if (clientId == null) {
            System.out.println(
                    "Não foi encontrado cliente para a resposta: "
                            + mensagem.correlationId()
            );
            return;
        }

        messagingTemplate.convertAndSendToUser(
                clientId,
                "/queue/respostas",
                mensagem
        );
    }
}
