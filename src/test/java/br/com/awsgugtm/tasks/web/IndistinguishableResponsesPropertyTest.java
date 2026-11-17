package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Propriedade 2: respostas de não proprietário e de id inexistente são indistinguíveis.
 * Usa o contexto completo e a SecurityFilterChain real (sem @WithMockUser, sem desabilitar filtros).
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
class IndistinguishableResponsesPropertyTest {

    private static final long ID_ALICE = 42L;
    private static final long ID_BOB = 43L;
    private static final String TITULO_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITULO_43 = "Atualizar planilha de custos (fictício)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository repository;

    // Feature: secure-task-access, Property 2: Respostas de não proprietário e de id inexistente são indistinguíveis
    // Validates: Requirements 1.5, 3.5, 4.1, 4.2, 4.3, 6.1, 6.2, 9.5
    @Property(tries = 100)
    void respostasDeNaoProprietarioEIdInexistenteSaoIndistinguiveis(
            @ForAll boolean tarefaDeBob,
            @ForAll @LongRange(min = 500000L, max = Long.MAX_VALUE) long idInexistente) throws Exception {
        Assume.that(!repository.existsById(idInexistente));

        long idDaTarefa = tarefaDeBob ? ID_BOB : ID_ALICE;
        String titulo = tarefaDeBob ? TITULO_43 : TITULO_42;
        String ownerReal = tarefaDeBob ? "bob" : "alice";
        // Usuário autenticado A, diferente do proprietário B.
        String usuarioA = tarefaDeBob ? "alice" : "bob";
        String senhaA = tarefaDeBob ? "alice-demo" : "bob-demo";

        List<String> titulosAntes = titulos();
        long antes = repository.count();

        MockHttpServletResponse alheia = mockMvc
                .perform(get("/api/tasks/" + idDaTarefa).with(httpBasic(usuarioA, senhaA)))
                .andReturn()
                .getResponse();
        MockHttpServletResponse inexistente = mockMvc
                .perform(get("/api/tasks/" + idInexistente).with(httpBasic(usuarioA, senhaA)))
                .andReturn()
                .getResponse();

        assertEquals(404, alheia.getStatus());
        assertEquals(404, inexistente.getStatus());
        assertEquals(cabecalhos(alheia), cabecalhos(inexistente), "cabeçalhos (exceto Date) devem ser iguais");
        assertArrayEquals(
                inexistente.getContentAsByteArray(), alheia.getContentAsByteArray(), "corpo deve ser idêntico");

        for (MockHttpServletResponse resposta : List.of(alheia, inexistente)) {
            String corpo = resposta.getContentAsString();
            for (String proibido : List.of(
                    "\"id\"", "\"title\"", "\"owner\"", titulo, ownerReal, String.valueOf(idDaTarefa),
                    String.valueOf(idInexistente))) {
                assertFalse(corpo.contains(proibido), "corpo não deve conter: " + proibido);
            }
        }

        assertEquals(2L, repository.count());
        assertEquals(antes, repository.count(), "nenhuma tarefa deve ser criada ou removida");
        assertEquals(titulosAntes, titulos(), "nenhuma tarefa deve ser alterada");
    }

    private List<String> titulos() {
        return repository.findAll().stream().map(Task::getTitle).sorted().toList();
    }

    private static Map<String, List<String>> cabecalhos(MockHttpServletResponse response) {
        Map<String, List<String>> mapa = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String nome : response.getHeaderNames()) {
            if (!HttpHeaders.DATE.equalsIgnoreCase(nome)) {
                mapa.put(nome, response.getHeaders(nome));
            }
        }
        return mapa;
    }
}
