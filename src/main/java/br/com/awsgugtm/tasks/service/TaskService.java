package br.com.awsgugtm.tasks.service;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    /** Listagem corretamente filtrada pelo usuário autenticado. */
    public List<Task> listByOwner(String username) {
        return repository.findByOwner(username);
    }

    // =====================================================================
    // ⚠️ VULNERÁVEL (BOLA / OWASP API1:2023 - Broken Object Level Authorization)
    // FALHA DIDÁTICA INTENCIONAL: busca somente por ID, sem verificar o owner.
    // Qualquer usuário autenticado lê a tarefa de qualquer outro usuário.
    //
    // Correção (NÃO aplicada neste laboratório):
    //     return repository.findByIdAndOwner(id, username);
    // (exige declarar findByIdAndOwner no TaskRepository)
    // =====================================================================
    public Optional<Task> findByIdVulnerable(Long id) {
        return repository.findById(id);
    }
}
