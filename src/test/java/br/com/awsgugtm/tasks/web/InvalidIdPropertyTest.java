package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Propriedade 8: id inválido gera 400 genérico, sem eco do valor enviado.
 * Usa a SecurityFilterChain real com httpBasic do usuário alice (dados fictícios).
 */
@JqwikSpringSupport
@SpringBootTest
@AutoConfigureMockMvc
class InvalidIdPropertyTest {

    private static final String AUTH_ALICE = "Basic "
            + Base64.getEncoder().encodeToString("alice:alice-demo".getBytes(StandardCharsets.UTF_8));
    private static final String TITULO_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITULO_43 = "Atualizar planilha de custos (fictício)";

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static final AtomicReference<String> CORPO_REFERENCIA = new AtomicReference<>();

    @Autowired
    private MockMvc mockMvc;

    // Feature: secure-task-access, Property 8: Id inválido gera 400 genérico sem eco
    // Validates: Requirements 6.3, 9.4, 9.5
    @Property(tries = 100)
    void idInvalidoGera400GenericoSemEco(@ForAll("idsInvalidos") String id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tasks/{id}", id).header("Authorization", AUTH_ALICE))
                .andReturn();

        assertEquals(400, result.getResponse().getStatus(), "status para id: " + id);

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Corpo constante entre tentativas.
        CORPO_REFERENCIA.compareAndSet(null, body);
        assertEquals(CORPO_REFERENCIA.get(), body, "corpo deve ser constante");

        // Sem eco do valor enviado.
        // (ids só com letras podem ser substring de texto fixo, ex. "da"; a constância acima já cobre esse caso)
        if (id.chars().anyMatch(c -> !Character.isLetter(c))) {
            assertFalse(body.contains(id), "corpo não deve ecoar o id");
        }

        // Sem nomes de classe, stack trace ou campos de tarefa.
        List<String> proibidos = List.of(
                "Exception", "br.com.awsgugtm", "java.", "org.springframework", "at ", "stackTrace", "trace",
                "\"id\"", "\"title\"", "\"owner\"", TITULO_42, TITULO_43);
        for (String proibido : proibidos) {
            assertFalse(body.contains(proibido), "corpo não deve conter: " + proibido);
        }
    }

    @Provide
    Arbitrary<String> idsInvalidos() {
        return Arbitraries.oneOf(letras(), decimais(), hexadecimais(), octais(), overflow(), comSinalMais());
    }

    private static Arbitrary<String> digitos(int min, int max) {
        return Arbitraries.strings().withCharRange('0', '9').ofMinLength(min).ofMaxLength(max);
    }

    /** Letras ASCII com ao menos uma letra não hexadecimal (g-z / G-Z). */
    private static Arbitrary<String> letras() {
        Arbitrary<Character> naoHex = Arbitraries.chars().range('g', 'z');
        Arbitrary<String> resto = Arbitraries.strings().withCharRange('a', 'z').withCharRange('A', 'Z')
                .ofMaxLength(10);
        return Combinators.combine(resto, naoHex, resto).as((a, c, b) -> a + c + b);
    }

    private static Arbitrary<String> decimais() {
        return Combinators.combine(digitos(1, 6), digitos(1, 6)).as((a, b) -> a + "." + b);
    }

    private static Arbitrary<String> hexadecimais() {
        return Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f').withCharRange('A', 'F')
                .ofMinLength(1).ofMaxLength(8).map(h -> "0x" + h);
    }

    private static Arbitrary<String> octais() {
        return Arbitraries.strings().withCharRange('0', '7').ofMinLength(1).ofMaxLength(8).map(o -> "0o" + o);
    }

    private static Arbitrary<String> overflow() {
        return Arbitraries.bigIntegers().between(LONG_MAX.add(BigInteger.ONE), LONG_MAX.multiply(BigInteger.valueOf(1000)))
                .map(BigInteger::toString);
    }

    private static Arbitrary<String> comSinalMais() {
        return digitos(1, 10).map(d -> "+" + d);
    }
}
