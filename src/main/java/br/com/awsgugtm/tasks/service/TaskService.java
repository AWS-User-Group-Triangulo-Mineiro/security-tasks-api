package br.com.awsgugtm.tasks.service;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String INVALID_USERNAME_MESSAGE = "Argumento invalido";

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    /** Listagem filtrada pelo usuário autenticado. */
    public List<Task> listByOwner(String username) {
        requireUsername(username);
        return repository.findByOwner(username);
    }

    /**
     * Busca a tarefa pelo id e pelo proprietário em uma única consulta.
     * Retorna vazio tanto para id inexistente quanto para tarefa de outro dono.
     */
    public Optional<Task> findByIdAndOwner(Long id, String username) {
        requireUsername(username);
        if (id == null) {
            return Optional.empty();
        }
        Optional<Task> result = repository.findByIdAndOwner(id, username);
        if (result.isEmpty()) {
            log.info("tarefa nao encontrada id={}", id);
        }
        return result;
    }

    private static void requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(INVALID_USERNAME_MESSAGE);
        }
    }
}
