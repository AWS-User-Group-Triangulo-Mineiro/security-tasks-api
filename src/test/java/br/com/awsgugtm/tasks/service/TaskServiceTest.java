package br.com.awsgugtm.tasks.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void donoConsultaPropriaTarefa_recebeResultado() {
        Task task = new Task(1001L, "tarefa ficticia", "usuario-a");
        when(repository.findByIdAndOwner(1001L, "usuario-a")).thenReturn(Optional.of(task));

        Optional<Task> result = service.findByIdAndOwner(1001L, "usuario-a");

        assertTrue(result.isPresent());
        assertSame(task, result.get());
        verify(repository).findByIdAndOwner(1001L, "usuario-a");
        verify(repository, never()).findById(anyLong());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void outroUsuario_recebeOptionalEmpty() {
        when(repository.findByIdAndOwner(1001L, "usuario-b")).thenReturn(Optional.empty());

        Optional<Task> result = service.findByIdAndOwner(1001L, "usuario-b");

        assertTrue(result.isEmpty());
        verify(repository).findByIdAndOwner(1001L, "usuario-b");
        verify(repository, never()).findById(anyLong());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void idInexistente_produzMesmoResultadoDeOutroUsuario() {
        when(repository.findByIdAndOwner(1001L, "usuario-b")).thenReturn(Optional.empty());
        when(repository.findByIdAndOwner(999999L, "usuario-b")).thenReturn(Optional.empty());

        Optional<Task> outroDono = service.findByIdAndOwner(1001L, "usuario-b");
        Optional<Task> inexistente = service.findByIdAndOwner(999999L, "usuario-b");

        assertEquals(outroDono, inexistente);
        assertTrue(inexistente.isEmpty());
        verify(repository, never()).findById(anyLong());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void findByIdAndOwner_usernameInvalido_lancaExcecaoSemConsultarRepositorio(String username) {
        assertThrows(IllegalArgumentException.class, () -> service.findByIdAndOwner(1001L, username));

        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void listByOwner_usernameInvalido_lancaExcecaoSemConsultarRepositorio(String username) {
        assertThrows(IllegalArgumentException.class, () -> service.listByOwner(username));

        verifyNoInteractions(repository);
    }

    @Test
    void idNulo_retornaOptionalEmptySemConsultarRepositorio() {
        Optional<Task> result = service.findByIdAndOwner(null, "usuario-a");

        assertTrue(result.isEmpty());
        verifyNoInteractions(repository);
        verify(repository, never()).findByIdAndOwner(any(), any());
    }
}
