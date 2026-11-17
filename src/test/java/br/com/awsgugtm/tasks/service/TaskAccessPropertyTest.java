package br.com.awsgugtm.tasks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Testes de propriedade do acesso por id. Usa H2 real via contexto Spring.
 * Os dados gerados usam ids a partir de 10000 e são removidos a cada tentativa,
 * então as tarefas 42 e 43 do seeder nunca são tocadas.
 */
@JqwikSpringSupport
@SpringBootTest
class TaskAccessPropertyTest {

    private static final long ID_BASE = 10_000L;
    private static final long ID_RANGE = 20L;

    @Autowired
    private TaskService service;

    @Autowired
    private TaskRepository repository;

    /** Spec de uma tarefa fictícia: id (faixa 10000+) e owner. */
    record TaskSpec(long id, String owner) {
    }

    // Feature: secure-task-access, Property 1: Acesso por id somente para o proprietário exato
    // Validates: Requirements 1.2, 3.1, 8.1, 8.4
    @Property(tries = 100)
    void acessoPorIdSomenteParaProprietarioExato(
            @ForAll("taskSpecs") List<TaskSpec> specs,
            @ForAll("queryIds") long queryId,
            @ForAll("usernames") String username) {

        // Garante unicidade de id dentro da tentativa (ids são atribuídos manualmente)
        List<Task> tasks = new ArrayList<>();
        List<Long> usedIds = new ArrayList<>();
        for (TaskSpec spec : specs) {
            if (!usedIds.contains(spec.id())) {
                usedIds.add(spec.id());
                tasks.add(new Task(spec.id(), "tarefa-ficticia-" + spec.id(), spec.owner()));
            }
        }

        try {
            repository.saveAll(tasks);

            Optional<Task> expected = tasks.stream()
                    .filter(t -> t.getId().equals(queryId) && t.getOwner().equals(username))
                    .findFirst();

            Optional<Task> actual = service.findByIdAndOwner(queryId, username);

            assertEquals(expected.isPresent(), actual.isPresent(),
                    "presença do resultado deve seguir a comparação exata de owner");
            expected.ifPresent(e -> {
                assertEquals(e.getId(), actual.get().getId());
                assertEquals(e.getOwner(), actual.get().getOwner());
                assertEquals(e.getTitle(), actual.get().getTitle());
            });
        } finally {
            // Remove somente os ids criados nesta tentativa (nunca 42 e 43)
            repository.deleteAllById(usedIds);
            repository.flush();
        }
    }

    @Provide
    Arbitrary<List<TaskSpec>> taskSpecs() {
        Arbitrary<Long> ids = Arbitraries.longs().between(ID_BASE, ID_BASE + ID_RANGE);
        return Combinators.combine(ids, usernames()).as(TaskSpec::new).list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<Long> queryIds() {
        return Arbitraries.longs().between(ID_BASE, ID_BASE + ID_RANGE);
    }

    /** Nomes conhecidos com variações de caixa e nomes aleatórios fictícios (nunca em branco). */
    @Provide
    Arbitrary<String> usernames() {
        Arbitrary<String> known = Arbitraries.of("alice", "bob", "carol")
                .flatMap(name -> Arbitraries.of(name, name.toUpperCase(), capitalize(name)));
        Arbitrary<String> random = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8)
                .flatMap(s -> Arbitraries.of(s, s.toUpperCase(), s.toLowerCase()));
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(3, known),
                net.jqwik.api.Tuple.of(1, random));
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
