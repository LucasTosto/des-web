package br.uff.tutoria.gateway;

import java.net.ConnectException;
import java.util.UUID;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
import br.uff.tutoria.comum.dto.RespostaMensagem;
import br.uff.tutoria.gateway.controller.PerguntaController;
import br.uff.tutoria.gateway.controller.SessaoController;
import br.uff.tutoria.gateway.service.CorrelationService;
import br.uff.tutoria.gateway.service.PerguntaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O RabbitMQ é simulado: os testes não exigem o broker ligado.
@WebMvcTest({PerguntaController.class, SessaoController.class})
@Import({PerguntaService.class, CorrelationService.class})
class PerguntaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CorrelationService sessoes;

    private String clientId;

    @BeforeEach
    void simularConfirmacaoDoBroker() {
        clientId = sessoes.criarSessao();
        simularResultado(true, false);
    }

    @Test
    void devePublicarAPerguntaNaFilaComOMesmoIdentificadorDevolvido() throws Exception {
        UUID correlationId = receberPergunta();

        ArgumentCaptor<PerguntaMensagem> mensagem = ArgumentCaptor.forClass(PerguntaMensagem.class);
        ArgumentCaptor<MessagePostProcessor> ajuste = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.PERGUNTAS_EXCHANGE),
            eq(RabbitMQConfig.PERGUNTAS_ROUTING_KEY),
            mensagem.capture(),
            ajuste.capture(),
            any(CorrelationData.class));

        assertEquals(correlationId.toString(), mensagem.getValue().correlationId());
        assertEquals("O que é escopo?", mensagem.getValue().texto());

        Message amqp = ajuste.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(MessageDeliveryMode.PERSISTENT, amqp.getMessageProperties().getDeliveryMode());
        assertEquals(correlationId.toString(), amqp.getMessageProperties().getCorrelationId());
    }

    @Test
    void deveGerarUmIdentificadorDiferenteParaCadaPergunta() throws Exception {
        assertNotEquals(receberPergunta(), receberPergunta());
    }

    @Test
    void deveRejeitarPerguntaEmBrancoOuSemTextoSemPublicar() throws Exception {
        for (String corpo : new String[]{"{\"texto\":\"   \"}", "{\"texto\":\"\"}", "{\"texto\":null}", "{}"}) {
            mockMvc.perform(post("/perguntas").header("X-Client-Id", clientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo))
                .andExpect(status().isBadRequest());
        }
        verify(rabbitTemplate, never()).convertAndSend(
            any(String.class), any(String.class), any(PerguntaMensagem.class),
            any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void deveResponder503QuandoORabbitMQEstiverIndisponivel() throws Exception {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
            .when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(PerguntaMensagem.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        mockMvc.perform(post("/perguntas").header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value(503));
    }

    @Test
    void deveResponder503QuandoBrokerConfirmaMasDevolveMensagemSemRota() throws Exception {
        simularResultado(true, true);
        verificarPublicacaoNaoConfirmada();
    }

    @Test
    void deveResponder503QuandoBrokerRejeitaPublicacao() throws Exception {
        simularResultado(false, false);
        verificarPublicacaoNaoConfirmada();
    }

    @Test
    void deveCriarSessaoERejeitarIdentificadorNaoEmitido() throws Exception {
        mockMvc.perform(post("/sessoes"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").isNotEmpty());

        mockMvc.perform(post("/perguntas").header("X-Client-Id", "inventado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecuperarRespostaApenasNaSessaoDaPergunta() throws Exception {
        String id = receberPergunta().toString();
        var consulta = "/perguntas/" + id + "/resposta";
        mockMvc.perform(get(consulta).header("X-Client-Id", clientId))
            .andExpect(status().isAccepted());
        sessoes.registrarResposta(RespostaMensagem.ok(id, "Resposta"));
        mockMvc.perform(get(consulta).header("X-Client-Id", sessoes.criarSessao()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get(consulta).header("X-Client-Id", clientId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correlation_id").value(id))
            .andExpect(jsonPath("$.texto").value("Resposta"));
    }

    @Test
    void devePublicarSomenteOHistoricoDaRespostaEscolhida() throws Exception {
        String primeira = receberPergunta().toString();
        sessoes.registrarResposta(RespostaMensagem.ok(primeira, "Um exemplo é uma receita."));
        String independente = receberPergunta().toString();
        sessoes.registrarResposta(RespostaMensagem.ok(independente, "Resposta de outro assunto"));

        String corpo = objectMapper.writeValueAsString(java.util.Map.of(
                "texto", "Pode dar outro exemplo?", "respostaAnteriorId", primeira));
        mockMvc.perform(post("/perguntas").header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isAccepted());

        var captor = ArgumentCaptor.forClass(PerguntaMensagem.class);
        verify(rabbitTemplate, org.mockito.Mockito.times(3)).convertAndSend(
                eq(RabbitMQConfig.PERGUNTAS_EXCHANGE), eq(RabbitMQConfig.PERGUNTAS_ROUTING_KEY),
                captor.capture(), any(MessagePostProcessor.class), any(CorrelationData.class));
        var historico = captor.getValue().historico();
        assertEquals(1, historico.size());
        assertEquals("Um exemplo é uma receita.", historico.getFirst().resposta());

        mockMvc.perform(post("/perguntas").header("X-Client-Id", sessoes.criarSessao())
                .contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isNotFound());
    }

    private void simularResultado(boolean ack, boolean devolvida) {
        doAnswer(invocation -> {
            CorrelationData dados = invocation.getArgument(4);
            if (devolvida) {
                dados.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitMQConfig.PERGUNTAS_EXCHANGE,
                    RabbitMQConfig.PERGUNTAS_ROUTING_KEY));
            }
            dados.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
            any(String.class), any(String.class), any(PerguntaMensagem.class),
            any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private void verificarPublicacaoNaoConfirmada() throws Exception {
        mockMvc.perform(post("/perguntas").header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.correlation_id").doesNotExist());
    }

    private UUID receberPergunta() throws Exception {
        String resposta = mockMvc.perform(post("/perguntas").header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resposta).get("correlation_id").asText());
    }
}
