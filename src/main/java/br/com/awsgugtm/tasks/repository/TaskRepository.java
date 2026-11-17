package br.com.awsgugtm.tasks.repository;

import br.com.awsgugtm.tasks.domain.Task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByOwner(String owner);

    /** Consulta atômica: retorna a tarefa somente se id e owner coincidirem no mesmo registro. */
    Optional<Task> findByIdAndOwner(Long id, String owner);
}
