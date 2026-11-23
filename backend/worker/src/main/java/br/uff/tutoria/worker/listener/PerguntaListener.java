package br.uff.tutoria.worker.listener;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.worker.ia.TutorIA;
import br.uff.tutoria.worker.prompt.EnriquecedorDePrompt;
import br.uff.tutoria.worker.prompt.PromptTutor;
import br.uff.tutoria.worker.publisher.RespostaPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PerguntaListener {

    private static final Logger LOG = LoggerFactory.getLogger(PerguntaListener.class);

    private final EnriquecedorDePrompt enriquecedor;
    private final TutorIA tutorIA;
    private final RespostaPublisher publisher;

    public PerguntaListener(EnriquecedorDePrompt enriquecedor, TutorIA tutorIA,
                            RespostaPublisher publisher) {
        this.enriquecedor = enriquecedor;
        this.tutorIA = tutorIA;
        this.publisher = publisher;
    }

    @RabbitListener(queues = RabbitMQConfig.PERGUNTAS_QUEUE)
    public void receber(PerguntaMensagem mensagem) {
        PromptTutor prompt = enriquecedor.enriquecer(mensagem);
        LOG.info("Pergunta preparada: correlation_id={}, tema={}, perfil={}, temperatura={}",
                prompt.correlationId(), prompt.tema(), prompt.perfil(), prompt.temperatura());

        RespostaMensagem resposta;
        try {
            String texto = tutorIA.responder(
                    prompt.instrucaoSistema(), prompt.pergunta(), prompt.temperatura());
            resposta = RespostaMensagem.ok(prompt.correlationId(), texto);
        } catch (RuntimeException e) {
            // Tentativas esgotadas: o aluno recebe um aviso em vez de esperar para sempre.
            LOG.error("Sem resposta da IA para correlation_id={}", prompt.correlationId());
            resposta = RespostaMensagem.erro(prompt.correlationId(),
                    "Não foi possível gerar a resposta agora. Tente novamente em instantes.");
        }

        try {
            publisher.publicar(resposta);
        } catch (AmqpException e) {
            LOG.warn("Resposta não confirmada para correlation_id={}; a pergunta será reenfileirada.",
                    resposta.correlationId());
            // Evita um ciclo de reentrega imediata enquanto a publicação estiver indisponível.
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException interrompida) {
                Thread.currentThread().interrupt();
            }
            throw new ImmediateRequeueAmqpException("Publicação da resposta não confirmada.", e);
        }
        LOG.info("Resposta publicada: correlation_id={}, status={}",
                resposta.correlationId(), resposta.status());
    }
}