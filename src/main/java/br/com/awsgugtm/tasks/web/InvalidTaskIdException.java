package br.com.awsgugtm.tasks.web;

/**
 * Identificador de tarefa fora do formato aceito. Sem mensagem dinamica e sem o valor recebido.
 */
public class InvalidTaskIdException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTaskIdException() {
        super("id invalido");
    }
}
