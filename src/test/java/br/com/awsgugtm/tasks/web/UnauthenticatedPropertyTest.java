package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.awsgugtm.tasks.repository.TaskRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Propriedade 3: requisições sem autenticação válida nunca devolvem dados.
 * Usa o contexto completo e a SecurityFilterChain real (sem @WithMockUser, sem desabilitar filtros).
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
class UnauthenticatedPropertyTest {

    private static final String TITULO_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITULO_43 = "Atualizar planilha de custos (fictício)";
    private static final String SENHA_ALICE = "alice-demo";
    private static final String SENHA_BOB = "bob-demo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository repository;

    // Feature: secure-task-access, Property 3: Requisição sem autenticação válida nunca devolve dados
    // Validates: Requirements 1.6, 4.4, 5.1, 5.3, 5.4, 11.4, 11.5
    @Property(tries = 100)
    void requisicaoSemAutenticacaoValidaNuncaDevolveDados(
            @ForAll long id, @ForAll("authorizationInvalido") String authorization) throws Exception {
        long antes = repository.count();

        assertSemDados(executar("/api/tasks/" + id, authorization));
        assertSemDados(executar("/api/tasks", authorization));

        assertEquals(antes, repository.count(), "estado armazenado não deve mudar");
    }

    private MvcResult executar(String url, String authorization) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (!authorization.isEmpty()) {
            request = request.header("Authorization", authorization);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static void assertSemDados(MvcResult result) throws Exception {
        assertEquals(401, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        for (String proibido : List.of("\"id\"", "\"title\"", "\"owner\"", TITULO_42, TITULO_43)) {
            assertFalse(body.contains(proibido), "corpo não deve conter: " + proibido);
        }
    }

    @Provide
    Arbitrary<String> authorizationInvalido() {
        // "" representa cabeçalho ausente.
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                semPrefixoBasic(),
                base64Invalido(),
                usuarioDesconhecido(),
                senhaIncorreta());
    }

    private static Arbitrary<String> alfanumerico(int min, int max) {
        return Arbitraries.strings().alpha().numeric().ofMinLength(min).ofMaxLength(max);
    }

    private static Arbitrary<String> semPrefixoBasic() {
        return alfanumerico(1, 30).filter(s -> !s.toLowerCase().startsWith("basic"));
    }

    private static Arbitrary<String> base64Invalido() {
        // Caracteres ASCII imprimíveis fora do alfabeto base64 (sem espaço e sem controle).
        return Arbitraries.strings().withChars('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '[', ']', '{', '}')
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> "Basic " + s);
    }

    private static Arbitrary<String> usuarioDesconhecido() {
        return alfanumerico(1, 12)
                .filter(u -> !u.equalsIgnoreCase("alice") && !u.equalsIgnoreCase("bob"))
                .flatMap(u -> alfanumerico(1, 12).map(p -> basic(u, p)));
    }

    private static Arbitrary<String> senhaIncorreta() {
        Arbitrary<String> alice = alfanumerico(1, 12)
                .filter(p -> !p.equals(SENHA_ALICE))
                .map(p -> basic("alice", p));
        Arbitrary<String> bob = alfanumerico(1, 12)
                .filter(p -> !p.equals(SENHA_BOB))
                .map(p -> basic("bob", p));
        return Arbitraries.oneOf(alice, bob);
    }

    private static String basic(String user, String password) {
        String token = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
