package br.uff.tutoria.comum.config;

import java.nio.charset.StandardCharsets;

import br.uff.tutoria.comum.dto.PerguntaMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    @Test
    void deveConverterMensagemParaJsonEDeVolta() throws Exception {
        var mapper = new ObjectMapper();
        var conversor = new RabbitMQConfig().jsonMessageConverter(mapper);
        var pergunta = new PerguntaMensagem("pergunta-1", "O que é escopo?");

        var mensagem = conversor.toMessage(pergunta, new MessageProperties());
        var json = mapper.readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));

        assertEquals("application/json", mensagem.getMessageProperties().getContentType());
        assertEquals(pergunta.correlationId(), json.get("correlation_id").asText());
        assertEquals(pergunta.texto(), json.get("texto").asText());
        assertEquals(pergunta, conversor.fromMessage(mensagem));
    }

    @Test
    void deveDeclararFilaEExchangeDuraveisComMesmaRotaDaPublicacao() {
        var config = new RabbitMQConfig();
        var fila = config.perguntasQueue();
        var exchange = config.perguntasExchange();
        var binding = config.perguntasBinding(fila, exchange);

        assertTrue(fila.isDurable());
        assertTrue(exchange.isDurable());
        assertEquals(fila.getName(), binding.getDestination());
        assertEquals(exchange.getName(), binding.getExchange());
        assertEquals(RabbitMQConfig.PERGUNTAS_ROUTING_KEY, binding.getRoutingKey());
    }
}
