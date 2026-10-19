package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenPricingTest {

    @Test
    @DisplayName("R11: cost = input × input price + output × output price, per million tokens, exactly")
    void exactCost() {
        TokenPricing p = TokenPricing.perMillionTokens(new BigDecimal("0.15"), new BigDecimal("0.60"));
        assertEquals(0, new BigDecimal("0.0003687").compareTo(p.cost(342L, 529L).orElseThrow()),
                () -> p.cost(342L, 529L).toString());
    }

    @Test
    @DisplayName("R11: FREE prices local models at zero")
    void free() {
        assertEquals(0, BigDecimal.ZERO.compareTo(TokenPricing.FREE.cost(342L, 529L).orElseThrow()));
    }

    @Test
    @DisplayName("R11: a missing token count means unknown cost, not zero")
    void unknownCounts() {
        TokenPricing p = TokenPricing.perMillionTokens(new BigDecimal("1"), new BigDecimal("1"));
        assertEquals(Optional.empty(), p.cost(null, 10L));
        assertEquals(Optional.empty(), p.cost(10L, null));
    }

    @Test
    @DisplayName("R11: negative prices are rejected")
    void negativeRejected() {
        assertThrows(IllegalArgumentException.class, () -> TokenPricing.perMillionTokens(new BigDecimal("-1"), BigDecimal.ONE));
        assertTrue(TokenPricing.FREE.inputPerMillion().signum() == 0);
    }
}
