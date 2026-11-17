package br.com.awsgugtm.tasks.repository;

import br.com.awsgugtm.tasks.domain.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByOwner(String owner);
}
