package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.awsgugtm.tasks.repository.TaskRepository;
import br.com.awsgugtm.tasks.service.TaskService;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/** Testes de fumaça da configuração local (usuários, seeder e remoção do método vulnerável). */
@SpringBootTest
@AutoConfigureMockMvc
class LocalConfigSmokeTest {

    private static final String TITULO_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITULO_43 = "Atualizar planilha de custos (fictício)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository repository;

    private static ResultMatcher semDadosDeTarefa() {
        return result -> {
            String body = result.getResponse().getContentAsString();
            for (String proibido : new String[] {"\"id\"", "\"title\"", "\"owner\"", TITULO_42, TITULO_43}) {
                assertFalse(body.contains(proibido), "corpo não deve conter: " + proibido);
            }
        };
    }

    @Test
    void aliceAutentica_retorna200EmListagem() throws Exception {
        mockMvc.perform(get("/api/tasks").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk());
    }

    @Test
    void bobAutentica_retorna200EmListagem() throws Exception {
        mockMvc.perform(get("/api/tasks").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioTerceiroFicticio_retorna401SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks").with(httpBasic("carol", "carol-demo")))
                .andExpect(status().isUnauthorized())
                .andExpect(semDadosDeTarefa());
    }

    @Test
    void senhaIncorreta_retorna401SemDados() throws Exception {
        mockMvc.perform(get("/api/tasks").with(httpBasic("alice", "senha-incorreta-ficticia")))
                .andExpect(status().isUnauthorized())
                .andExpect(semDadosDeTarefa());
        mockMvc.perform(get("/api/tasks").with(httpBasic("bob", "senha-incorreta-ficticia")))
                .andExpect(status().isUnauthorized())
                .andExpect(semDadosDeTarefa());
    }

    @Test
    void seederCriaExatamenteDuasTarefas_42Alice_e_43Bob() {
        assertEquals(2, repository.count());
        assertTrue(repository.findByIdAndOwner(42L, "alice").isPresent());
        assertTrue(repository.findByIdAndOwner(43L, "bob").isPresent());
    }

    @Test
    void taskServiceNaoDeclaraFindByIdVulnerable() {
        boolean declara = Arrays.stream(TaskService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("findByIdVulnerable"::equals);
        assertFalse(declara, "TaskService não deve declarar findByIdVulnerable");
    }
}
