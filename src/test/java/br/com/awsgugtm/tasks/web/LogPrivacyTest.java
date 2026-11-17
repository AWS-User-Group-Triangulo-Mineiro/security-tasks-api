package br.com.awsgugtm.tasks.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que os logs não vazam senhas, valor do cabeçalho Authorization, PII,
 * título/owner de tarefas nem o usuário informado em credenciais inválidas.
 * Todos os dados são fictícios.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LogPrivacyTest {

    private static final String NOT_FOUND_INDICATOR = "tarefa nao encontrada";
    private static final String TITLE_42 = "Revisar relatório trimestral (fictício)";
    private static final String TITLE_43 = "Atualizar planilha de custos (fictício)";
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern CPF = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern ID_VALUE = Pattern.compile("id=\\d+");

    @Autowired
    private MockMvc mockMvc;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    // (a) nenhuma entrada contém senha, valor de Authorization ou PII

    @Test
    void logsNaoContemSenhaAuthorizationNemPii() throws Exception {
        String bobHeaderValue = basicValue("bob", "bob-demo");
        String aliceHeaderValue = basicValue("alice", "alice-demo");

        mockMvc.perform(get("/api/tasks").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("alice", "alice-demo")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/999999").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isUnauthorized());

        for (String text : allLogTexts()) {
            assertFalse(text.contains("alice-demo"), "senha de alice em log: " + text);
            assertFalse(text.contains("bob-demo"), "senha de bob em log: " + text);
            assertFalse(text.contains(bobHeaderValue), "Authorization de bob em log: " + text);
            assertFalse(text.contains(aliceHeaderValue), "Authorization de alice em log: " + text);
            assertFalse(text.contains("Basic " + bobHeaderValue), "cabecalho Authorization em log: " + text);
            assertFalse(EMAIL.matcher(text).find(), "e-mail (PII) em log: " + text);
            assertFalse(CPF.matcher(text).find(), "documento (PII) em log: " + text);
        }
    }

    // (b) tarefa não encontrada: uma única entrada, com id e indicador fixo

    @Test
    void tarefaDeOutroDono_geraUmaUnicaEntradaComIdEIndicadorFixo() throws Exception {
        mockMvc.perform(get("/api/tasks/42").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());

        List<String> notFound = notFoundEntries();
        assertEquals(1, notFound.size(), "esperada uma unica entrada: " + notFound);
        String entry = notFound.get(0);
        assertTrue(entry.contains("42"), "entrada deve conter o id: " + entry);
        assertNoTaskData(entry);
    }

    @Test
    void idInexistente_geraUmaUnicaEntradaComIdEIndicadorFixo() throws Exception {
        mockMvc.perform(get("/api/tasks/999999").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());

        List<String> notFound = notFoundEntries();
        assertEquals(1, notFound.size(), "esperada uma unica entrada: " + notFound);
        String entry = notFound.get(0);
        assertTrue(entry.contains("999999"), "entrada deve conter o id: " + entry);
        assertNoTaskData(entry);
    }

    @Test
    void mensagemIgualParaIdInexistenteETarefaDeOutroDono_desconsiderandoId() throws Exception {
        mockMvc.perform(get("/api/tasks/999999").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());
        List<String> missing = notFoundEntries();
        appender.list.clear();

        mockMvc.perform(get("/api/tasks/42").with(httpBasic("bob", "bob-demo")))
                .andExpect(status().isNotFound());
        List<String> foreign = notFoundEntries();

        assertEquals(1, missing.size());
        assertEquals(1, foreign.size());
        assertEquals(normalizeId(missing.get(0)), normalizeId(foreign.get(0)),
                "mensagens devem ser iguais exceto pelo id");
    }

    // (c) Authorization inválido ou ausente não ecoa valor nem usuário

    @Test
    void credenciaisComUsuarioDesconhecidoESenhaErrada_naoSaoEcoadasNosLogs() throws Exception {
        String headerValue = basicValue("intruso", "senha-errada");

        mockMvc.perform(get("/api/tasks/42").with(httpBasic("intruso", "senha-errada")))
                .andExpect(status().isUnauthorized());

        for (String text : allLogTexts()) {
            assertFalse(text.contains("intruso"), "usuario informado em log: " + text);
            assertFalse(text.contains("senha-errada"), "senha informada em log: " + text);
            assertFalse(text.contains(headerValue), "valor do Authorization em log: " + text);
        }
    }

    @Test
    void senhaErradaDeUsuarioValido_naoEhEcoadaNosLogs() throws Exception {
        String headerValue = basicValue("alice", "senha-errada");

        mockMvc.perform(get("/api/tasks").with(httpBasic("alice", "senha-errada")))
                .andExpect(status().isUnauthorized());

        for (String text : allLogTexts()) {
            assertFalse(text.contains("senha-errada"), "senha informada em log: " + text);
            assertFalse(text.contains(headerValue), "valor do Authorization em log: " + text);
        }
    }

    @Test
    void base64Invalido_naoEhEcoadoNosLogs() throws Exception {
        String invalid = "!!!base64-invalido!!!";

        mockMvc.perform(get("/api/tasks/42").header("Authorization", "Basic " + invalid))
                .andExpect(status().isUnauthorized());

        for (String text : allLogTexts()) {
            assertFalse(text.contains(invalid), "valor invalido do Authorization em log: " + text);
        }
    }

    @Test
    void authorizationAusente_naoGeraEntradaComDadosDeTarefa() throws Exception {
        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isUnauthorized());

        for (String text : allLogTexts()) {
            assertFalse(text.contains("Authorization:"), "cabecalho em log: " + text);
            assertNoTaskData(text);
        }
    }

    // utilitários

    private static String basicValue(String user, String password) {
        return Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeId(String text) {
        return ID_VALUE.matcher(text).replaceAll("id=<id>");
    }

    private static void assertNoTaskData(String text) {
        assertFalse(text.contains(TITLE_42), "titulo da tarefa 42 em log: " + text);
        assertFalse(text.contains(TITLE_43), "titulo da tarefa 43 em log: " + text);
        assertFalse(text.contains("Revisar relat"), "titulo em log: " + text);
        assertFalse(text.contains("Atualizar planilha"), "titulo em log: " + text);
        assertFalse(text.contains("alice"), "owner real em log: " + text);
        assertFalse(text.contains("owner"), "campo owner em log: " + text);
    }

    private List<String> notFoundEntries() {
        List<String> result = new ArrayList<>();
        for (String text : allLogTexts()) {
            if (text.contains(NOT_FOUND_INDICATOR)) {
                result.add(text);
            }
        }
        return result;
    }

    /** Mensagem formatada, mais mensagens de eventuais exceções anexadas. */
    private List<String> allLogTexts() {
        List<String> texts = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            StringBuilder sb = new StringBuilder(String.valueOf(event.getFormattedMessage()));
            IThrowableProxy proxy = event.getThrowableProxy();
            while (proxy != null) {
                sb.append(' ').append(proxy.getClassName()).append(": ").append(proxy.getMessage());
                proxy = proxy.getCause();
            }
            texts.add(sb.toString());
        }
        return texts;
    }
}
