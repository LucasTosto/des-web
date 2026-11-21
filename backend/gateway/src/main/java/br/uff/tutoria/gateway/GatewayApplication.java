package br.uff.tutoria.gateway;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// A configuração do RabbitMQ fica no módulo comum, fora do pacote do Gateway,
// por isso é importada explicitamente.
@SpringBootApplication
@Import(RabbitMQConfig.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
