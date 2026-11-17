package br.com.awsgugtm.tasks.web;

/**
 * Tarefa nao encontrada para o usuario autenticado (inexistente ou de outro dono).
 * Sem mensagem dinamica e sem dados da requisicao.
 */
public class TaskNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskNotFoundException() {
        super("tarefa nao encontrada");
    }
}
