package br.com.awsgugtm.tasks.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.awsgugtm.tasks.domain.Task;
import br.com.awsgugtm.tasks.dto.TaskResponse;
import br.com.awsgugtm.tasks.service.TaskService;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato controller -> service com TaskService mockado.
 * Usa a cadeia de filtros real de seguranca (httpBasic), sem desabilitar controles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerServiceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    // (a) o service recebe exatamente (id, username do Principal)
    @Test
    void getById_repassaIdEUsernameDoPrincipalAutenticado() throws Exception {
        when(taskService.findByIdAndOwner(42L, "alice"))
                .thenReturn(Optional.of(new Task(42L, "Tarefa ficticia", "alice")));

        mockMvc.perform(get("/api/tasks/42").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));

        verify(taskService).findByIdAndOwner(42L, "alice");
        verifyNoMoreInteractions(taskService);
    }

    // (a) username enviado pelo cliente (query/cabecalho) nao e repassado
    @Test
    void getById_ignoraOwnerEnviadoPeloClienteEmQueryECabecalho() throws Exception {
        when(taskService.findByIdAndOwner(anyLong(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tasks/42")
                        .param("owner", "alice")
                        .param("username", "alice")
                        .header("X-Owner", "alice")
                        .header("X-User", "alice")
                        .with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());

        verify(taskService).findByIdAndOwner(42L, "bob");
        verify(taskService, never()).findByIdAndOwner(anyLong(), org.mockito.ArgumentMatchers.eq("alice"));
        verifyNoMoreInteractions(taskService);
    }

    @Test
    void list_repassaSomenteUsernameDoPrincipalAutenticado() throws Exception {
        when(taskService.listByOwner("bob")).thenReturn(List.of(new Task(43L, "Tarefa ficticia", "bob")));

        mockMvc.perform(get("/api/tasks")
                        .param("owner", "alice")
                        .header("X-Owner", "alice")
                        .with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(43));

        verify(taskService).listByOwner("bob");
        verifyNoMoreInteractions(taskService);
    }

    @Test
    void semCredenciais_retorna401ENaoChamaService() throws Exception {
        mockMvc.perform(get("/api/tasks/42")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());

        verifyNoInteractions(taskService);
    }

    // (b) Principal nulo: controller chamado diretamente
    @Test
    void getById_principalNulo_lancaExcecaoDeAutenticacaoENaoChamaService() {
        TaskService mockService = mock(TaskService.class);
        TaskController controller = new TaskController(mockService);

        assertThatThrownBy(() -> controller.getById("42", null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(mockService);
    }

    @Test
    void list_principalNulo_lancaExcecaoDeAutenticacaoENaoChamaService() {
        TaskService mockService = mock(TaskService.class);
        TaskController controller = new TaskController(mockService);

        assertThatThrownBy(() -> controller.list(null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verifyNoInteractions(mockService);
    }

    // (c) tipos de retorno dos metodos do controller
    @Test
    void metodosDeEndpointRetornamSomenteTaskResponseOuListaDeTaskResponse() throws Exception {
        Method getById = TaskController.class.getMethod("getById", String.class, java.security.Principal.class);
        Method list = TaskController.class.getMethod("list", java.security.Principal.class);

        assertThat(getById.getReturnType()).isEqualTo(TaskResponse.class);
        assertThat(list.getReturnType()).isEqualTo(List.class);

        Type generic = list.getGenericReturnType();
        assertThat(generic).isInstanceOf(ParameterizedType.class);
        assertThat(((ParameterizedType) generic).getActualTypeArguments()).containsExactly(TaskResponse.class);

        for (Method m : TaskController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                assertThat(m.getReturnType()).isNotEqualTo(Task.class);
                assertThat(m.getReturnType()).isNotEqualTo(Optional.class);
            }
        }
    }
}
