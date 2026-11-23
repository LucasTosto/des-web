package br.uff.tutoria.gateway.websocket;

import br.uff.tutoria.gateway.service.CorrelationService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final CorrelationService sessoes;

    public StompAuthInterceptor(CorrelationService sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        var comando = accessor.getCommand();
        if (comando == StompCommand.CONNECT || comando == StompCommand.STOMP) {
            String token = accessor.getFirstNativeHeader("client-id");
            if (!sessoes.sessaoValida(token)) throw new IllegalArgumentException("Sessão inválida.");
            accessor.setUser(() -> token);
        } else if (comando == StompCommand.SUBSCRIBE) {
            if (accessor.getUser() == null || !sessoes.sessaoValida(accessor.getUser().getName())
                    || !"/user/queue/respostas".equals(accessor.getDestination())) {
                throw new IllegalArgumentException("Inscrição não permitida.");
            }
        } else if (comando == StompCommand.SEND) {
            throw new IllegalArgumentException("Use HTTP para enviar perguntas.");
        }
        return message;
    }
}
