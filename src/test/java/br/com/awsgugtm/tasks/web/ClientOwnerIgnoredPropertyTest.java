package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Propriedade 4: owner/usuário/identidade enviados pelo cliente (query e cabeçalhos) não alteram a resposta.
 * Usa o contexto completo e a SecurityFilterChain real (httpBasic, sem @WithMockUser).
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
class ClientOwnerIgnoredPropertyTest {

    private static final List<String> QUERY_NAMES = List.of("owner", "user", "username");
    private static final List<String> HEADER_NAMES = List.of("X-Owner", "X-User", "X-Forwarded-User");

    @Autowired
    private MockMvc mockMvc;

    // Feature: secure-task-access, Property 4: Owner enviado pelo cliente é ignorado
    // Validates: Requirements 1.7, 2.2, 3.4
    @Property(tries = 100)
    void ownerEnviadoPeloClienteEIgnorado(
            @ForAll("usuarios") String usuario,
            @ForAll("ids") long id,
            @ForAll("valoresOwner") Map<String, String> queryParams,
            @ForAll("valoresOwnerCabecalho") Map<String, String> headers) throws Exception {
        String senha = usuario + "-demo";
        String url = "/api/tasks/" + id;

        MockHttpServletResponse base = mockMvc.perform(get(url).with(httpBasic(usuario, senha)))
                .andReturn().getResponse();

        MockHttpServletRequestBuilder comOwner = get(url).with(httpBasic(usuario, senha));
        queryParams.forEach(comOwner::queryParam);
        headers.forEach(comOwner::header);
        MockHttpServletResponse enviado = mockMvc.perform(comOwner).andReturn().getResponse();

        assertEquals(base.getStatus(), enviado.getStatus(), "status deve ser idêntico");
        assertEquals(cabecalhos(base), cabecalhos(enviado), "cabeçalhos (exceto Date) devem ser idênticos");
        assertEquals(base.getContentAsString(), enviado.getContentAsString(), "corpo deve ser idêntico");
    }

    private static Map<String, List<String>> cabecalhos(MockHttpServletResponse response) {
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : response.getHeaderNames()) {
            if ("Date".equalsIgnoreCase(name)) {
                continue;
            }
            result.put(name, new ArrayList<>(response.getHeaders(name)));
        }
        return result;
    }

    @Provide
    Arbitrary<String> usuarios() {
        return Arbitraries.of("alice", "bob");
    }

    @Provide
    Arbitrary<Long> ids() {
        return Arbitraries.oneOf(
                Arbitraries.just(42L),
                Arbitraries.just(43L),
                Arbitraries.longs().between(500_000L, 9_999_999_999L));
    }

    private static Arbitrary<String> valorOwner() {
        Arbitrary<String> ficticio = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(16);
        return Arbitraries.oneOf(Arbitraries.of("alice", "bob"), ficticio);
    }

    private static Arbitrary<Map<String, String>> subconjunto(List<String> nomes) {
        // Valor por nome; ao menos um nome presente.
        return Arbitraries.of(nomes).list().ofMinSize(1).ofMaxSize(nomes.size()).uniqueElements()
                .flatMap(escolhidos -> valorOwner().list().ofSize(escolhidos.size()).map(valores -> {
                    Map<String, String> map = new LinkedHashMap<>();
                    for (int i = 0; i < escolhidos.size(); i++) {
                        map.put(escolhidos.get(i), valores.get(i));
                    }
                    return map;
                }));
    }

    @Provide
    Arbitrary<Map<String, String>> valoresOwner() {
        return subconjunto(QUERY_NAMES);
    }

    @Provide
    Arbitrary<Map<String, String>> valoresOwnerCabecalho() {
        return subconjunto(HEADER_NAMES);
    }
}
