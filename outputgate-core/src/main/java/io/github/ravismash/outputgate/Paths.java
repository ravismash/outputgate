package io.github.ravismash.outputgate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
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
        return resolve(root, path, true);
    }

    /**
     * Like {@link #resolve(JsonNode, String)}, but when {@code unwrapLast} is false the
     * value found at the end of the path is returned as written, evidence object and all.
     */
    static List<Hit> resolve(JsonNode root, String path, boolean unwrapLast) {
        List<Hit> current = List.of(new Hit("", unwrap(root)));
        String[] segments = path.split("\\.");
        for (int s = 0; s < segments.length; s++) {
            String segment = segments[s];
            boolean last = s == segments.length - 1;
            boolean each = segment.endsWith("[*]");
            String name = each ? segment.substring(0, segment.length() - 3) : segment;
            List<Hit> next = new ArrayList<>();
            for (Hit hit : current) {
                String here = hit.path().isEmpty() ? name : hit.path() + "." + name;
                if (hit.node().isMissingNode()) {
                    next.add(hit);
                    continue;
                }
                JsonNode raw = hit.node().path(name);
                JsonNode child = each || !last || unwrapLast ? unwrap(raw) : raw;
                if (!each) {
                    next.add(new Hit(here, child));
                } else if (!child.isArray()) {
                    next.add(new Hit(here, MissingNode.getInstance()));
                } else {
                    for (int i = 0; i < child.size(); i++) {
                        JsonNode element = child.get(i);
                        next.add(new Hit(here + "[" + i + "]", last && !unwrapLast ? element : unwrap(element)));
                    }
                }
            }
            current = next;
        }
        return current;
    }

    /** A copy of {@code node} with every evidence object replaced by its value, at any depth. */
    static JsonNode unwrapAll(JsonNode node) {
        JsonNode n = unwrap(node);
        if (n.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, JsonNode> e : n.properties()) {
                copy.set(e.getKey(), unwrapAll(e.getValue()));
            }
            return copy;
        }
        if (n.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode(n.size());
            for (JsonNode element : n) {
                copy.add(unwrapAll(element));
            }
            return copy;
        }
        return n;
    }

    static JsonNode unwrap(JsonNode node) {
        return node.isObject() && node.has("value") ? node.get("value") : node;
    }
}
