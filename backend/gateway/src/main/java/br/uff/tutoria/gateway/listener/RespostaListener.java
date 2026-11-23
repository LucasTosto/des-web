package br.uff.tutoria.gateway.listener;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.gateway.service.CorrelationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RespostaListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final CorrelationService correlationService;

    public RespostaListener(SimpMessagingTemplate messagingTemplate, CorrelationService correlationService) {
        this.messagingTemplate = messagingTemplate;
        this.correlationService = correlationService;
    }

    @RabbitListener(queues = RabbitMQConfig.RESPOSTAS_QUEUE)
    public void receber(RespostaMensagem mensagem) {
        // Guarda o resultado antes de notificar o navegador, que pode estar desconectado.
        var entrega = correlationService.registrarResposta(mensagem);
        if (entrega != null) {
            messagingTemplate.convertAndSendToUser(entrega.cliente(), "/queue/respostas", entrega.resposta());
        }
    }
}
