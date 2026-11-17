package br.com.awsgugtm.tasks.web;

import br.com.awsgugtm.tasks.dto.TaskResponse;
import br.com.awsgugtm.tasks.service.TaskService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** Seguro: lista apenas as tarefas do usuário autenticado. */
    @GetMapping
    public List<TaskResponse> list(Principal principal) {
        return service.listByOwner(principal.getName()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    // =====================================================================
    // ⚠️ VULNERÁVEL (BOLA / OWASP API1:2023): o usuário autenticado (Principal)
    // NÃO é usado para checar o dono da tarefa. Alice lê tarefas de Bob e vice-versa.
    // Correção (não aplicada): service.findByIdAndOwner(id, principal.getName()).
    // =====================================================================
    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable Long id) {
        return service.findByIdVulnerable(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada"));
    }
}
