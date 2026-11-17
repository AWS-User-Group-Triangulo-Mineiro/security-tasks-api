package br.com.awsgugtm.tasks.web;

import br.com.awsgugtm.tasks.dto.TaskResponse;
import br.com.awsgugtm.tasks.service.TaskService;
import java.security.Principal;
import java.util.List;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Lista apenas as tarefas do usuário autenticado. */
    @GetMapping
    public List<TaskResponse> list(Principal principal) {
        String username = requireUsername(principal);
        return service.listByOwner(username).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Retorna a tarefa somente se pertencer ao usuário autenticado.
     * Tarefa de outro dono e id inexistente produzem o mesmo resultado (404).
     */
    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable("id") String rawId, Principal principal) {
        String username = requireUsername(principal);
        long id = TaskIdParser.parse(rawId);
        return service.findByIdAndOwner(id, username)
                .map(TaskResponse::from)
                .orElseThrow(TaskNotFoundException::new);
    }

    private static String requireUsername(Principal principal) {
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("Autenticação ausente");
        }
        return principal.getName();
    }
}
