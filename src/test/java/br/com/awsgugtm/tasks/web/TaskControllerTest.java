package br.com.awsgugtm.tasks.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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

    /**
     * TESTE DE CARACTERIZAÇÃO: documenta a falha BOLA ATUAL (intencional).
     * Bob NÃO deveria ler a tarefa 42 (de alice), mas hoje recebe 200.
     * Ao corrigir a falha, inverta a expectativa para 404 (ou 403).
     */
    @Test
    void caracterizacao_BOLA_bobAcessaTarefa42DeAlice_retorna200() throws Exception {
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }
}
