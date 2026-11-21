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

    // Sem confirmação conclusiva (por exemplo, timeout), a mensagem pode ter chegado à fila.
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleAmqp(AmqpException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
            LocalDateTime.now(),
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE.name(),
            request.getMethod(),
            request.getRequestURI(),
            "Não foi possível confirmar o envio da pergunta."
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
