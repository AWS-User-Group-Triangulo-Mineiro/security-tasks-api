package br.com.awsgugtm.tasks.web;

/**
 * Validacao estrita do id do caminho: somente {@code -?[0-9]+} dentro do intervalo de {@code long}.
 * O overflow e decidido por {@code Long.parseLong}. Funcao pura, sem I/O.
 */
public final class TaskIdParser {

    private TaskIdParser() {
    }

    public static long parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidTaskIdException();
        }
        int start = raw.charAt(0) == '-' ? 1 : 0;
        if (start == raw.length()) {
            throw new InvalidTaskIdException();
        }
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                throw new InvalidTaskIdException();
            }
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new InvalidTaskIdException();
        }
    }
}
