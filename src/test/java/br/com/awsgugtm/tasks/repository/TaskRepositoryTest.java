package br.com.awsgugtm.tasks.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.awsgugtm.tasks.domain.Task;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Testa a consulta atômica findByIdAndOwner com H2 real.
 * Em @DataJpaTest o DataSeeder (CommandLineRunner com @Component) não é carregado,
 * então os testes usam apenas tarefas fictícias com ids fora de 42 e 43.
 */
@DataJpaTest
class TaskRepositoryTest {

    private static final long ID_ALICE = 9001L;
    private static final long ID_BOB = 9002L;
    private static final long ID_INEXISTENTE = 9999L;

    @Autowired
    private TaskRepository repository;

    @BeforeEach
    void setUp() {
        repository.save(new Task(ID_ALICE, "Tarefa ficticia da alice", "alice"));
        repository.save(new Task(ID_BOB, "Tarefa ficticia do bob", "bob"));
        repository.flush();
    }

    @Test
    void ownerCorreto_encontraTarefa() {
        Optional<Task> result = repository.findByIdAndOwner(ID_ALICE, "alice");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(ID_ALICE);
        assertThat(result.get().getOwner()).isEqualTo("alice");
    }

    @Test
    void ownerErrado_retornaVazio() {
        assertThat(repository.findByIdAndOwner(ID_ALICE, "bob")).isEmpty();
        assertThat(repository.findByIdAndOwner(ID_BOB, "alice")).isEmpty();
    }

    @Test
    void ownerEmOutraCaixa_retornaVazio() {
        assertThat(repository.findByIdAndOwner(ID_ALICE, "ALICE")).isEmpty();
        assertThat(repository.findByIdAndOwner(ID_ALICE, "Alice")).isEmpty();
    }

    @Test
    void idInexistente_retornaVazio() {
        assertThat(repository.findByIdAndOwner(ID_INEXISTENTE, "alice")).isEmpty();
    }
}
