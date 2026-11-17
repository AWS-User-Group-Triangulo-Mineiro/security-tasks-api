package br.com.awsgugtm.tasks.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TaskIdParserPropertyTest {

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    // Feature: secure-task-access, Property 7: O parser de id aceita somente inteiros decimais dentro do intervalo de Long
    // **Validates: Requirements 9.3, 9.4**
    @Property(tries = 200)
    void parserAcceptsOnlyDecimalIntegersWithinLongRange(@ForAll("ids") String raw) {
        if (isValidLongDecimal(raw)) {
            assertThat(TaskIdParser.parse(raw)).isEqualTo(new BigInteger(raw).longValueExact());
        } else {
            assertThatThrownBy(() -> TaskIdParser.parse(raw)).isInstanceOf(InvalidTaskIdException.class);
        }
    }

    private static boolean isValidLongDecimal(String raw) {
        if (raw == null || !raw.matches("-?[0-9]+")) {
            return false;
        }
        BigInteger value = new BigInteger(raw);
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }

    @Provide
    Arbitrary<String> ids() {
        Arbitrary<String> arbitrary = Arbitraries.strings().ofMaxLength(30);

        Arbitrary<String> validLongs = Arbitraries.longs().map(String::valueOf);

        Arbitrary<String> digits = Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(25);

        Arbitrary<String> signedDigits = Arbitraries.of("", "-", "+")
                .flatMap(sign -> digits.map(d -> sign + d));

        Arbitrary<String> leadingZeros = Arbitraries.integers().between(1, 40)
                .flatMap(n -> Arbitraries.longs().greaterOrEqual(0L)
                        .flatMap(v -> Arbitraries.of("", "-").map(sign -> sign + "0".repeat(n) + v)));

        Arbitrary<String> overflow = Arbitraries.bigIntegers()
                .between(LONG_MAX.add(BigInteger.ONE), LONG_MAX.multiply(BigInteger.valueOf(1000)))
                .flatMap(v -> Arbitraries.of("", "-").map(sign -> sign + v));

        Arbitrary<String> boundaries = Arbitraries.of(
                String.valueOf(Long.MAX_VALUE), String.valueOf(Long.MIN_VALUE),
                LONG_MAX.add(BigInteger.ONE).toString(), LONG_MIN.subtract(BigInteger.ONE).toString(),
                "-0", "0", "000000000000000000000000001", "-000000000000000000000000001");

        Arbitrary<String> hexOctal = Arbitraries.strings().withCharRange('0', 'f').ofMinLength(1).ofMaxLength(10)
                .flatMap(s -> Arbitraries.of("0x", "0X", "#", "-0x", "0", "").map(p -> p + s));

        Arbitrary<String> special = Arbitraries.of(
                "", " ", " 1", "1 ", "1 2", "+1", "1.0", "1.", ".1", "1e3", "-", "--1", "-+1", "abc", "0x1F",
                "#1F", "010", "\u0661\u0662", "1\n", "\t1", "\u00a01");

        Arbitrary<String> padded = validLongs.flatMap(s -> Arbitraries.of(" ", "\t", "\n", "+").map(p -> p + s));

        return Arbitraries.oneOf(arbitrary, validLongs, digits, signedDigits, leadingZeros, overflow, boundaries,
                hexOctal, special, padded).injectNull(0.03);
    }
}
