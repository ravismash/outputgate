package io.github.ravismash.outputgate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An {@link LlmClient} for offline tests (R10): canned replies, no network, no API key.
 * A prompt it has no reply for is an error, never an empty answer.
 */
public final class FakeLlmClient implements LlmClient {

    /** Picks the reply for (prompt, call number starting at 0). */
    private final BiFunction<String, Integer, String> replies;
    private final Usage usage;
    private final List<String> prompts = Collections.synchronizedList(new ArrayList<>());

    private FakeLlmClient(BiFunction<String, Integer, String> replies, Usage usage) {
        this.replies = replies;
        this.usage = usage;
    }

    /** @param reply the text returned for every prompt @return the fake */
    public static FakeLlmClient replying(String reply) {
        if (reply == null) {
            throw new IllegalArgumentException("reply is null");
        }
        return new FakeLlmClient((prompt, call) -> reply, Usage.UNKNOWN);
    }

    /** @param replies texts returned one per call, in order; a call after the last is an error @return the fake */
    public static FakeLlmClient inOrder(String... replies) {
        if (replies == null || replies.length == 0) {
            throw new IllegalArgumentException("inOrder needs at least one reply");
        }
        List<String> list = List.of(replies);
        return new FakeLlmClient((prompt, call) -> {
            if (call >= list.size()) {
                throw new IllegalStateException("FakeLlmClient has only " + list.size() + " replies; call " + (call + 1) + " has none");
            }
            return list.get(call);
        }, Usage.UNKNOWN);
    }

    /** @param replies reply per exact prompt; any other prompt is an error @return the fake */
    public static FakeLlmClient byPrompt(Map<String, String> replies) {
        if (replies == null || replies.isEmpty()) {
            throw new IllegalArgumentException("byPrompt needs at least one reply");
        }
        Map<String, String> copy = Map.copyOf(replies);
        return new FakeLlmClient((prompt, call) -> {
            String reply = copy.get(prompt);
            if (reply == null) {
                throw new IllegalStateException("FakeLlmClient has no reply for prompt: " + prompt);
            }
            return reply;
        }, Usage.UNKNOWN);
    }

    /** @param usage the usage reported with every reply (default {@link Usage#UNKNOWN}) @return a copy with that usage */
    public FakeLlmClient withUsage(Usage usage) {
        return new FakeLlmClient(replies, usage == null ? Usage.UNKNOWN : usage);
    }

    @Override
    public LlmOutput complete(String prompt) {
        int call;
        synchronized (prompts) {
            call = prompts.size();
            prompts.add(prompt);
        }
        return new LlmOutput(replies.apply(prompt, call), usage);
    }

    /** @return every prompt received so far, in order */
    public List<String> prompts() {
        synchronized (prompts) {
            return List.copyOf(prompts);
        }
    }
}
