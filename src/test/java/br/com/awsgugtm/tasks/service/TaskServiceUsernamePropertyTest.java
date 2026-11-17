package br.com.awsgugtm.tasks.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.awsgugtm.tasks.repository.TaskRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Feature: secure-task-access, Property 9: Username inválido é rejeitado antes de consultar o repositório
 *
 * <b>Validates: Requirements 2.4, 2.6</b>
 */
class TaskServiceUsernamePropertyTest {

    @Provide
    Arbitrary<String> invalidUsernames() {
        Arbitrary<String> blank = Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(1)
                .ofMaxLength(20);
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.just(""),
                blank);
    }

    @Provide
    Arbitrary<Long> anyIds() {
        return Arbitraries.longs().injectNull(0.1);
    }

    @Property(tries = 200)
    void findByIdAndOwnerRejectsInvalidUsername(
            @ForAll("invalidUsernames") String username,
            @ForAll("anyIds") Long id) {
        TaskRepository repository = mock(TaskRepository.class);
        TaskService service = new TaskService(repository);

        assertThatThrownBy(() -> service.findByIdAndOwner(id, username))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Property(tries = 200)
    void listByOwnerRejectsInvalidUsername(@ForAll("invalidUsernames") String username) {
        TaskRepository repository = mock(TaskRepository.class);
        TaskService service = new TaskService(repository);

        assertThatThrownBy(() -> service.listByOwner(username))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
