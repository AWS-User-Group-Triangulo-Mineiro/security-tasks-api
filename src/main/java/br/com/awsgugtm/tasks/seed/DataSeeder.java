package br.com.awsgugtm.tasks.seed;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Dados fictícios do laboratório. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final TaskRepository repository;

    public DataSeeder(TaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        repository.save(new Task(42L, "Revisar relatório trimestral (fictício)", "alice"));
        repository.save(new Task(43L, "Atualizar planilha de custos (fictício)", "bob"));
    }
}
