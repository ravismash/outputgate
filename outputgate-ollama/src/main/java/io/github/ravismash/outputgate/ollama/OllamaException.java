package io.github.ravismash.outputgate.ollama;

/** Ollama answered with an error, or could not be reached. */
public final class OllamaException extends RuntimeException {

    /** HTTP status, or 0 when Ollama was not reached. */
    private final int status;

    /** @param status HTTP status, or 0 when Ollama was not reached @param message what went wrong @param cause the cause, or null */
    public OllamaException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** @return the HTTP status, or 0 when Ollama was not reached */
    public int status() {
        return status;
    }
}
