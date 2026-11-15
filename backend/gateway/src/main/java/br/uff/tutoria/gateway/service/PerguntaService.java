package br.uff.tutoria.gateway.service;

import java.util.UUID;

import br.uff.tutoria.gateway.dto.PerguntaDto;
import br.uff.tutoria.gateway.dto.PerguntaRecebidaDto;
import org.springframework.stereotype.Service;

@Service
public class PerguntaService {

    public PerguntaRecebidaDto receber(PerguntaDto pergunta) {
        // Nesta etapa, a pergunta é apenas validada e identificada.
        // A publicação da pergunta no RabbitMQ entra na etapa 2.
        return new PerguntaRecebidaDto(UUID.randomUUID().toString());
    }
}
