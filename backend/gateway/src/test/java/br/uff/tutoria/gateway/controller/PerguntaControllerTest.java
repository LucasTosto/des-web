package br.uff.tutoria.gateway.controller;

import java.util.UUID;

import br.uff.tutoria.gateway.service.PerguntaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PerguntaController.class)
@Import(PerguntaService.class)
class PerguntaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveGerarUmIdentificadorDiferenteParaCadaPergunta() throws Exception {
        assertNotEquals(receberPergunta(), receberPergunta());
    }

    @Test
    void deveRejeitarPerguntaEmBrancoOuSemTexto() throws Exception {
        for (String corpo : new String[]{"{\"texto\":\"   \"}", "{\"texto\":\"\"}", "{\"texto\":null}", "{}"}) {
            mockMvc.perform(post("/perguntas")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpo))
                .andExpect(status().isBadRequest());
        }
    }

    private UUID receberPergunta() throws Exception {
        String resposta = mockMvc.perform(post("/perguntas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"O que é escopo?\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resposta).get("correlation_id").asText());
    }
}
