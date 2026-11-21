package br.uff.tutoria.gateway.controller;

import java.net.ConnectException;
import java.util.UUID;

import br.uff.tutoria.comum.config.RabbitMQConfig;
import br.uff.tutoria.comum.dto.PerguntaMensagem;
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
import org.springframework.amqp.rabbit.core.RabbitOperations;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O RabbitMQ é simulado: os testes não exigem o broker ligado.
@WebMvcTest(PerguntaController.class)
@Import(PerguntaService.class)
class PerguntaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void executarPublicacaoNoMock() {
        // invoke() recebe um callback; o mock o executa passando a si mesmo como canal.
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<Object> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });
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
            ajuste.capture());
        verify(rabbitTemplate).waitForConfirmsOrDie(anyLong());

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
            mockMvc.perform(post("/perguntas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo))
                .andExpect(status().isBadRequest());
        }
        verify(rabbitTemplate, never()).invoke(any());
    }

    @Test
    void deveResponder503QuandoORabbitMQEstiverIndisponivel() throws Exception {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
            .when(rabbitTemplate).invoke(any());

        mockMvc.perform(post("/perguntas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value(503));
    }

    private UUID receberPergunta() throws Exception {
        String resposta = mockMvc.perform(post("/perguntas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resposta).get("correlation_id").asText());
    }
}
