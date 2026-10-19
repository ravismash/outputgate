package io.github.ravismash.outputgate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FRD invariant (p.1): "No gate calls a model." No gate type may even mention LlmClient. */
class NoGateCallsAModelTest {

    private static final List<Class<?>> GATES = List.of(SchemaGate.class, TypeGate.class, MathGate.class,
            ProvenanceGate.class, BudgetGate.class, RegexGate.class);

    @Test
    @DisplayName("INV-1: no gate has an LlmClient field, constructor parameter or method parameter")
    void noGateReferencesLlmClient() {
        List<String> found = new ArrayList<>();
        for (Class<?> gate : GATES) {
            List<Class<?>> all = new ArrayList<>(List.of(gate.getDeclaredClasses()));
            all.add(gate);
            for (Class<?> c : all) {
                for (Field f : c.getDeclaredFields()) {
                    if (LlmClient.class.isAssignableFrom(f.getType())) found.add(c.getSimpleName() + "." + f.getName());
                }
                for (Constructor<?> k : c.getDeclaredConstructors()) {
                    if (Arrays.stream(k.getParameterTypes()).anyMatch(LlmClient.class::isAssignableFrom)) found.add(c.getSimpleName() + "()");
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (Arrays.stream(m.getParameterTypes()).anyMatch(LlmClient.class::isAssignableFrom)) found.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertTrue(found.isEmpty(), "gates must not touch a model: " + found);
    }
}
