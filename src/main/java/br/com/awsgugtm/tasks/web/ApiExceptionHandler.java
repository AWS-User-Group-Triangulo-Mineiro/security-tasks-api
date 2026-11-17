package br.com.awsgugtm.tasks.web;

import br.com.awsgugtm.tasks.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte excecoes em respostas de erro genericas. Todos os corpos vem de constantes:
 * nunca incluem id, path, timestamp, username, titulo ou owner.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final ErrorResponse NOT_FOUND = new ErrorResponse("NOT_FOUND", "Tarefa não encontrada");
    private static final ErrorResponse BAD_REQUEST = new ErrorResponse("BAD_REQUEST", "Requisição inválida");
    private static final ErrorResponse INTERNAL_ERROR = new ErrorResponse("INTERNAL_ERROR", "Erro interno");

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound() {
        return json(HttpStatus.NOT_FOUND, NOT_FOUND);
    }

    @ExceptionHandler({InvalidTaskIdException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest() {
        return json(HttpStatus.BAD_REQUEST, BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Void> handleUnauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        // Excecoes de seguranca e excecoes padrao do Spring MVC seguem o tratamento original.
        if (ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || ex instanceof org.springframework.web.ErrorResponse) {
            throw ex;
        }
        // Somente o tipo da excecao: sem mensagem e sem stack trace.
        log.error("erro interno tipo={}", ex.getClass().getName());
        return json(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
    }

    private static ResponseEntity<ErrorResponse> json(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
