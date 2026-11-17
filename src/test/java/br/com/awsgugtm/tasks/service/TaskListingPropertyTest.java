package br.com.awsgugtm.tasks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de propriedade da listagem por dono, com H2 real.
 * Usa ids na faixa 20000+ e usernames fictícios próprios, sem tocar nas tarefas 42 e 43.
 */
@JqwikSpringSupport
@SpringBootTest
class TaskListingPropertyTest {

    private static final long MIN_ID = 20000L;
    private static final long MAX_ID = 20999L;
    private static final String[] OWNERS = {"p6-user-a", "p6-user-b", "p6-user-c", "p6-user-d"};

    @Autowired
    private TaskService service;

    @Autowired
    private TaskRepository repository;

    @Provide
    Arbitrary<List<Long>> taskIds() {
        return Arbitraries.longs().between(MIN_ID, MAX_ID).list().uniqueElements().ofMaxSize(15);
    }

    @Provide
    Arbitrary<List<Integer>> ownerIndexes() {
        return Arbitraries.integers().between(0, OWNERS.length - 1).list().ofMaxSize(15);
    }

    @Provide
    Arbitrary<String> queriedUsername() {
        // Inclui um usuário que nunca possui tarefas (lista vazia esperada).
        return Arbitraries.of("p6-user-a", "p6-user-b", "p6-user-c", "p6-user-d", "p6-user-sem-tarefas");
    }

    // Feature: secure-task-access, Property 6: Listagem contém exatamente as tarefas do usuário, sem duplicatas
    // **Validates: Requirements 1.3, 7.1, 7.2, 7.5**
    @Property(tries = 100)
    void listagemContemExatamenteAsTarefasDoUsuarioSemDuplicatas(
            @ForAll("taskIds") List<Long> ids,
            @ForAll("ownerIndexes") List<Integer> ownerIdx,
            @ForAll("queriedUsername") String username) {

        List<Task> created = new ArrayList<>();
        Set<Long> expected = new TreeSet<>();
        int n = Math.min(ids.size(), ownerIdx.size());
        for (int i = 0; i < n; i++) {
            String owner = OWNERS[ownerIdx.get(i)];
            created.add(new Task(ids.get(i), "tarefa-ficticia-" + ids.get(i), owner));
            if (owner.equals(username)) {
                expected.add(ids.get(i));
            }
        }

        try {
            repository.saveAll(created);

            List<Task> result = service.listByOwner(username);

            List<Long> resultIds = result.stream().map(Task::getId).toList();
            Set<Long> resultSet = new HashSet<>(resultIds);
            assertEquals(resultIds.size(), resultSet.size(), "a listagem não deve conter duplicatas");
            assertEquals(expected, new TreeSet<>(resultSet), "ids devem ser exatamente os do usuário");
            for (Task t : result) {
                assertEquals(username, t.getOwner(), "todas as tarefas devem pertencer ao usuário");
            }
            if (expected.isEmpty()) {
                assertEquals(0, result.size(), "usuário sem tarefas recebe lista vazia");
            }
        } finally {
            for (Task t : created) {
                repository.deleteById(t.getId());
            }
        }
    }
}
