package me.blockcraft.system;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes single values of a YAML file, addressed by a dotted path, and leaves the rest of the file as the operator
 * wrote it, comments and blank lines included. The file is replaced in one move, so a crash never leaves half of it.
 */
final class ConfigEditor {

    private final Path file;
    private final Yaml yaml;
    private final MappingNode root;

    private ConfigEditor(Path file, Yaml yaml, MappingNode root) {
        this.file = file;
        this.yaml = yaml;
        this.root = root;
    }

    static ConfigEditor open(Path file) throws IOException {
        var loader = new LoaderOptions();
        loader.setProcessComments(true);
        var dumper = new DumperOptions();
        dumper.setProcessComments(true);
        dumper.setDefaultFlowStyle(FlowStyle.BLOCK);
        dumper.setIndicatorIndent(2);
        dumper.setIndentWithIndicator(true);
        dumper.setWidth(Integer.MAX_VALUE);
        var yaml = new Yaml(new Constructor(loader), new Representer(dumper), dumper, loader);
        try (var reader = Files.newBufferedReader(file)) {
            var root = yaml.compose(reader) instanceof MappingNode mapping ? mapping : mapping();
            return new ConfigEditor(file, yaml, root);
        }
    }

    ConfigEditor set(String path, int value) {
        return put(path, new ScalarNode(Tag.INT, Integer.toString(value), null, null, ScalarStyle.PLAIN));
    }

    ConfigEditor set(String path, String value) {
        return put(path, text(value));
    }

    ConfigEditor set(String path, List<String> values) {
        return put(path, new SequenceNode(Tag.SEQ, values.stream().<Node>map(ConfigEditor::text).toList(), FlowStyle.BLOCK));
    }

    void save() throws IOException {
        var temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary)) {
            yaml.serialize(root, writer);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
    }

    private ConfigEditor put(String path, Node value) {
        var keys = path.split("\\.");
        var section = root;
        for (int i = 0; i < keys.length - 1; i++) {
            section = section(section, keys[i]);
        }
        replace(section, keys[keys.length - 1], value);
        return this;
    }

    private static MappingNode section(MappingNode parent, String key) {
        var existing = parent.getValue().stream().filter(tuple -> named(tuple, key)).findFirst();
        if (existing.isPresent() && existing.get().getValueNode() instanceof MappingNode found) {
            return found;
        }
        var section = mapping();
        replace(parent, key, section);
        return section;
    }

    private static void replace(MappingNode parent, String key, Node value) {
        var tuples = parent.getValue();
        for (int i = 0; i < tuples.size(); i++) {
            if (named(tuples.get(i), key)) {
                tuples.set(i, new NodeTuple(tuples.get(i).getKeyNode(), value));
                return;
            }
        }
        tuples.add(new NodeTuple(new ScalarNode(Tag.STR, key, null, null, ScalarStyle.PLAIN), value));
    }

    private static boolean named(NodeTuple tuple, String key) {
        return tuple.getKeyNode() instanceof ScalarNode name && name.getValue().equals(key);
    }

    private static ScalarNode text(String value) {
        return new ScalarNode(Tag.STR, value, null, null, ScalarStyle.DOUBLE_QUOTED);
    }

    private static MappingNode mapping() {
        return new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
    }
}
