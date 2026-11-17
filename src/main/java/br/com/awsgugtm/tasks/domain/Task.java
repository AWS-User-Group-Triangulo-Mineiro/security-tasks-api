package br.com.awsgugtm.tasks.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task {

    // ID atribuído manualmente (sem geração automática)
    @Id
    private Long id;

    private String title;

    // username do dono da tarefa
    private String owner;

    protected Task() {
    }

    public Task(Long id, String title, String owner) {
        this.id = id;
        this.title = title;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOwner() {
        return owner;
    }
}
