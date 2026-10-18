package io.github.ravismash.outputgate;

/**
 * One reason an output failed a gate.
 *
 * @param gate the gate that reported it
 * @param path the field it concerns, or an empty string for the whole output
 * @param message what is wrong
 */
public record Violation(String gate, String path, String message) {}
