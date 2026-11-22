package br.uff.tutoria.comum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configuração compartilhada por Gateway e Worker (cada um a importa com @Import).
// O RabbitMQ recusa declarar duas vezes a mesma fila com argumentos diferentes
// (erro PRECONDITION_FAILED). Mantendo a declaração em um único lugar, os dois lados
// sempre declaram a fila da mesma forma, e qualquer um deles pode subir primeiro.
@Configuration
public class RabbitMQConfig {

    public static final String PERGUNTAS_EXCHANGE = "perguntas.exchange";
    public static final String PERGUNTAS_QUEUE = "perguntas.queue";
    public static final String PERGUNTAS_ROUTING_KEY = "perguntas";

    // Exchange do tipo direct: entrega a mensagem às filas cuja routing key do binding
    // é igual à routing key usada na publicação.
    // Durável: a exchange continua existindo após o RabbitMQ ser reiniciado.
    @Bean
    public DirectExchange perguntasExchange() {
        return ExchangeBuilder.directExchange(PERGUNTAS_EXCHANGE).durable(true).build();
    }

    // Fila durável: sua definição e as mensagens persistentes que estão nela são gravadas
    // em disco e sobrevivem a um reinício do RabbitMQ.
    @Bean
    public Queue perguntasQueue() {
        return QueueBuilder.durable(PERGUNTAS_QUEUE).build();
    }

    @Bean
    public Binding perguntasBinding(Queue perguntasQueue, DirectExchange perguntasExchange) {
        return BindingBuilder.bind(perguntasQueue).to(perguntasExchange).with(PERGUNTAS_ROUTING_KEY);
    }

    // Substitui a serialização Java padrão por JSON, usando o ObjectMapper configurado
    // pelo Spring Boot. O Spring Boot aplica este bean ao RabbitTemplate (publicação) e
    // aos @RabbitListener (consumo). Apenas classes do pacote dto do módulo comum são
    // aceitas na desserialização.
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper, "br.uff.tutoria.comum.dto");
    }

    public static final String RESPOSTAS_EXCHANGE = "respostas.exchange";
    public static final String RESPOSTAS_QUEUE = "respostas.queue";
    public static final String RESPOSTAS_ROUTING_KEY = "respostas";

    @Bean
    public DirectExchange respostasExchange() {
        return ExchangeBuilder.directExchange(RESPOSTAS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue respostasQueue() {
        return QueueBuilder.durable(RESPOSTAS_QUEUE).build();
    }

    @Bean
    public Binding respostasBinding(Queue respostasQueue, DirectExchange respostasExchange) {
        return BindingBuilder.bind(respostasQueue).to(respostasExchange).with(RESPOSTAS_ROUTING_KEY);
    }
}
