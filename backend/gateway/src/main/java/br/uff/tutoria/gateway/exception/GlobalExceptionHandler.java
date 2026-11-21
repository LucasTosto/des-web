package br.uff.tutoria.gateway.exception;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    // RabbitMQ desligado, inacessível ou sem confirmar a publicação: a pergunta não foi
    // guardada na fila. Devolve 503 em vez de 500, pois o problema é um serviço externo
    // temporariamente indisponível, e não um erro do Gateway.
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleAmqp(AmqpException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE.name(),
            request.getMethod(),
            request.getRequestURI(),
            "Fila de perguntas indisponível. Tente novamente em instantes."
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
