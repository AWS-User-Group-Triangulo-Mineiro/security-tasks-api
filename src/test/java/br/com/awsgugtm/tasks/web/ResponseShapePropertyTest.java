package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Propriedade 5: respostas de sucesso expõem somente id e title.
 * Usa o contexto completo, a SecurityFilterChain real e httpBasic do dono (alice ou bob).
 * Ids na faixa 30000-30999; nunca altera as tarefas 42 e 43.
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
class ResponseShapePropertyTest {

    private static final long MIN_ID = 30000L;
    private static final long MAX_ID = 30999L;
    private static final String[] OWNERS = {"alice", "bob"};
    private static final Map<String, String> SENHAS = Map.of("alice", "alice-demo", "bob", "bob-demo");

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository repository;

    @Provide
    Arbitrary<List<Long>> taskIds() {
        return Arbitraries.longs().between(MIN_ID, MAX_ID).list().uniqueElements().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<Integer>> ownerIndexes() {
        return Arbitraries.integers().between(0, OWNERS.length - 1).list().ofMinSize(10).ofMaxSize(10);
    }

    // Feature: secure-task-access, Property 5: Resposta de sucesso expõe somente id e title
    // **Validates: Requirements 1.4, 3.2, 3.3, 7.3, 9.1**
    @Property(tries = 100)
    void respostaDeSucessoExpoeSomenteIdETitle(
            @ForAll("taskIds") List<Long> ids,
            @ForAll("ownerIndexes") List<Integer> ownerIdx) throws Exception {

        List<Task> created = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            created.add(new Task(id, "tarefa-ficticia-" + id, OWNERS[ownerIdx.get(i)]));
        }

        try {
            repository.saveAll(created);

            for (Task t : created) {
                String owner = t.getOwner();
                MvcResult single = mockMvc.perform(get("/api/tasks/" + t.getId())
                                .with(httpBasic(owner, SENHAS.get(owner))))
                        .andReturn();
                assertEquals(200, single.getResponse().getStatus());
                JsonNode node = mapper.readTree(single.getResponse().getContentAsString());
                assertShape(node, t);

                MvcResult list = mockMvc.perform(get("/api/tasks")
                                .with(httpBasic(owner, SENHAS.get(owner))))
                        .andReturn();
                assertEquals(200, list.getResponse().getStatus());
                JsonNode array = mapper.readTree(list.getResponse().getContentAsString());
                assertEquals(true, array.isArray());
                JsonNode found = null;
                for (JsonNode item : array) {
                    assertEquals(Set.of("id", "title"), fieldNames(item), "item da lista só expõe id e title");
                    if (item.get("id").asLong() == t.getId()) {
                        found = item;
                    }
                }
                assertNotNull(found, "a tarefa do dono deve aparecer na listagem");
                assertShape(found, t);
            }
        } finally {
            for (Task t : created) {
                repository.deleteById(t.getId());
            }
        }
    }

    private static void assertShape(JsonNode node, Task t) {
        assertEquals(Set.of("id", "title"), fieldNames(node), "somente id e title, sem owner");
        assertEquals(t.getId().longValue(), node.get("id").asLong());
        assertEquals(t.getTitle(), node.get("title").asText());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
