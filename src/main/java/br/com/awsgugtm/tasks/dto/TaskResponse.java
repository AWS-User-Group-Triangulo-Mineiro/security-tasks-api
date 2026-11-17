package br.com.awsgugtm.tasks.dto;

import br.com.awsgugtm.tasks.domain.Task;

public record TaskResponse(Long id, String title) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle());
    }
}
