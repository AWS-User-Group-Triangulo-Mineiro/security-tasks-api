package br.com.awsgugtm.tasks.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aliceAcessaPropriaTarefa42_retorna200() throws Exception {
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void anonimo_retorna401() throws Exception {
        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tarefaInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/api/tasks/999").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listagemRetornaApenasTarefasDoUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/api/tasks").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(42));
    }

    /** Regressão BOLA: Bob não pode ler a tarefa 42 (de alice) e não recebe nenhum dado dela. */
    @Test
    void bobAcessaTarefa42DeAlice_retorna404() throws Exception {
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("\"id\""))))
                .andExpect(content().string(not(containsString("\"title\""))))
                .andExpect(content().string(not(containsString("\"owner\""))))
                .andExpect(content().string(not(containsString("42"))))
                .andExpect(content().string(not(containsString("Revisar relatório trimestral"))))
                .andExpect(content().string(not(containsString("alice"))));
    }

    // ---- Requisito 12 (testes adicionais) ----

    private static final String TITULO_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITULO_43 = "Atualizar planilha de custos (fictício)";

    private static RequestPostProcessor alice() {
        return httpBasic("alice", "alice-demo");
    }

    private static RequestPostProcessor bob() {
        return httpBasic("bob", "bob-demo");
    }

    /** Corpo sem nenhum campo nem valor da tarefa 43 (de bob). */
    private static ResultMatcher semDadosDaTarefa43() {
        return result -> {
            String body = result.getResponse().getContentAsString();
            for (String proibido : new String[] {"\"id\"", "\"title\"", "\"owner\"", "43", TITULO_43, "bob"}) {
                org.junit.jupiter.api.Assertions.assertFalse(body.contains(proibido),
                        "corpo não deve conter: " + proibido);
            }
        };
    }

    /** Corpo sem nenhum campo de tarefa nem valores das tarefas 42 e 43. */
    private static ResultMatcher semDadosDeTarefa() {
        return result -> {
            String body = result.getResponse().getContentAsString();
            for (String proibido : new String[] {"\"id\"", "\"title\"", "\"owner\"", TITULO_42, TITULO_43}) {
                org.junit.jupiter.api.Assertions.assertFalse(body.contains(proibido),
                        "corpo não deve conter: " + proibido);
            }
        };
    }

    @Test
    void bobAcessaPropriaTarefa43_retorna200() throws Exception {
        mockMvc.perform(get("/api/tasks/43").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(43))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void aliceAcessaTarefa43DeBob_retorna404SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks/43").with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(semDadosDaTarefa43());
    }

    @Test
    void bobConsulta42Alheia_e_999999Inexistente_respostasIdenticas() throws Exception {
        MvcResult alheia = mockMvc.perform(get("/api/tasks/42").with(bob())).andReturn();
        MvcResult inexistente = mockMvc.perform(get("/api/tasks/999999").with(bob())).andReturn();

        MockHttpServletResponse a = alheia.getResponse();
        MockHttpServletResponse b = inexistente.getResponse();
        assertEquals(404, a.getStatus());
        assertEquals(a.getStatus(), b.getStatus());
        assertEquals(a.getContentAsString(), b.getContentAsString());

        assertEquals(headersSemData(a), headersSemData(b));
    }

    private static Map<String, List<String>> headersSemData(MockHttpServletResponse response) {
        Map<String, List<String>> headers = new TreeMap<>();
        for (String name : response.getHeaderNames()) {
            if (!"Date".equalsIgnoreCase(name)) {
                headers.put(name.toLowerCase(Locale.ROOT), response.getHeaders(name));
            }
        }
        return headers;
    }

    @Test
    void anonimo_tarefa42_retorna401SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isUnauthorized())
                .andExpect(semDadosDeTarefa());
    }

    @Test
    void anonimo_tarefaInexistente999999_retorna401SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks/999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(semDadosDeTarefa());
    }

    @Test
    void aliceConsultaInexistente999999_retorna404SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks/999999").with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(semDadosDeTarefa());
    }

    @Test
    void listagemDeAliceContemExatamenteTarefa42() throws Exception {
        mockMvc.perform(get("/api/tasks").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].owner").doesNotExist());
    }

    @Test
    void listagemDeBobContemExatamenteTarefa43() throws Exception {
        mockMvc.perform(get("/api/tasks").with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(43))
                .andExpect(jsonPath("$[0].owner").doesNotExist());
    }

    @Test
    void bobComOwnerAliceEmQueryParam_naoAcessaTarefa42() throws Exception {
        mockMvc.perform(get("/api/tasks/42").param("owner", "alice").with(bob()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("\"id\""))))
                .andExpect(content().string(not(containsString("\"title\""))))
                .andExpect(content().string(not(containsString(TITULO_42))));
    }

    @Test
    void bobComOwnerAliceEmCabecalho_naoAcessaTarefa42() throws Exception {
        mockMvc.perform(get("/api/tasks/42").header("X-Owner", "alice").with(bob()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("\"id\""))))
                .andExpect(content().string(not(containsString("\"title\""))))
                .andExpect(content().string(not(containsString(TITULO_42))));
    }

    @Test
    void aliceComOwnerBobEmQueryParam_acessaTarefa42() throws Exception {
        mockMvc.perform(get("/api/tasks/42").param("owner", "bob").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void aliceComOwnerBobEmCabecalho_acessaTarefa42() throws Exception {
        mockMvc.perform(get("/api/tasks/42").header("X-Owner", "bob").with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "0x1F", "9223372036854775808"})
    void idInvalido_retorna400ComCorpoGenericoSemEco(String idInvalido) throws Exception {
        mockMvc.perform(get("/api/tasks/" + idInvalido).with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString(idInvalido))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("br.com.awsgugtm"))))
                .andExpect(semDadosDeTarefa());
    }
}
