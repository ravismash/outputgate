package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A small path syntax: dot-separated field names, where {@code name[*]} visits every
 * element of an array, e.g. {@code line_items[*].amount}. Every step reads through
 * evidence objects ({@code {"value": ...}}), so both shapes resolve the same (R9).
 */
final class Paths {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]+(\\[\\*])?");

    /** One resolved location; {@code node} is missing when nothing is there. */
    record Hit(String path, JsonNode node) {}

    private Paths() {}

    /** Throws if the path is not in the supported syntax. */
    static String validate(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path is empty");
        }
        for (String segment : path.split("\\.", -1)) {
            if (!SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("bad path segment '" + segment + "' in " + path);
            }
        }
        return path;
    }

    static List<Hit> resolve(JsonNode root, String path) {
        List<Hit> current = List.of(new Hit("", unwrap(root)));
        for (String segment : path.split("\\.")) {
            boolean each = segment.endsWith("[*]");
            String name = each ? segment.substring(0, segment.length() - 3) : segment;
            List<Hit> next = new ArrayList<>();
            for (Hit hit : current) {
                String here = hit.path().isEmpty() ? name : hit.path() + "." + name;
                if (hit.node().isMissingNode()) {
                    next.add(hit);
                    continue;
                }
                JsonNode child = unwrap(hit.node().path(name));
                if (!each) {
                    next.add(new Hit(here, child));
                } else if (!child.isArray()) {
                    next.add(new Hit(here, MissingNode.getInstance()));
                } else {
                    for (int i = 0; i < child.size(); i++) {
                        next.add(new Hit(here + "[" + i + "]", unwrap(child.get(i))));
                    }
                }
            }
            current = next;
        }
        return current;
    }

    static JsonNode unwrap(JsonNode node) {
        return node.isObject() && node.has("value") ? node.get("value") : node;
    }
}
