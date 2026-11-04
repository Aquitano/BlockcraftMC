package me.blockcraft.common.message;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * What a player learns once and then expects everywhere: a click hint in a menu is one of BlockcraftAPI's
 * {@code <left>}, {@code <right>} and {@code <click>} tags, and a menu or dialog title starts with {@code <arrow>} and a
 * colour, with any detail after {@code <dark>·}.
 */
public class MessageStyleTest {

    /** A line that opens with a click word; "Klicke, um …" is a sentence in a chat hover, not a hint. */
    private static final Pattern HAND_WRITTEN_HINT =
            Pattern.compile("^(?:<[^>]+>|\\s)*(?:Linksklick|Rechtsklick|Klicken|Klicke)(?![,\\w])");
    private static final String PALETTE = "palette.";
    private static final Pattern TITLE = Pattern.compile("^<arrow><[\\w-]+>[^●]+$");
    private static final Pattern MENU = Pattern.compile("extends (?:List)?Menu\\b");
    private static final Pattern MENU_TITLE = Pattern.compile("super\\(([^;]*);");
    private static final Pattern DIALOG_TITLE = Pattern.compile("(?:DialogBase\\.builder|Dialogs\\.create)\\(([^\\n]*)");
    private static final Pattern KEY = Pattern.compile("\"([a-z0-9][a-z0-9.-]*)\"");

    @Test
    public void menusHintAtClicksWithTheHintTags() throws IOException {
        var handWritten = new ArrayList<String>();
        for (Path module : MessageFiles.modules()) {
            if (!module.startsWith(MessageFiles.ROOT.resolve("paper"))) {
                continue;
            }
            MessageFiles.bundled(module).forEach((key, value) -> value.lines()
                    .filter(line -> !key.startsWith(PALETTE) && HAND_WRITTEN_HINT.matcher(line).find())
                    .forEach(line -> handWritten.add(MessageFiles.ROOT.relativize(module) + " " + key + ": " + line)));
        }
        assertEquals(String.join("\n", handWritten), List.of(), handWritten);
    }

    @Test
    public void menuAndDialogTitlesShareOneShape() throws IOException {
        var api = MessageFiles.bundled(MessageFiles.API);
        var games = new ArrayList<Map<String, String>>();
        for (Path module : MessageFiles.modules()) {
            games.add(MessageFiles.bundled(module));
        }
        var offending = new ArrayList<String>();
        for (Path module : MessageFiles.modules()) {
            var own = MessageFiles.bundled(module);
            for (Path source : MessageFiles.sources(module)) {
                String code = Files.readString(source);
                var calls = new ArrayList<String>();
                if (MENU.matcher(code).find()) {
                    MENU_TITLE.matcher(code).results().forEach(call -> calls.add(call.group(1)));
                }
                DIALOG_TITLE.matcher(code).results().forEach(call -> calls.add(call.group(1)));
                for (String call : calls) {
                    KEY.matcher(call).results().map(key -> key.group(1)).forEach(key ->
                            title(key, own, api, games).filter(title -> !TITLE.matcher(title).matches())
                                    .ifPresent(title -> offending.add(source.getFileName() + " " + key + ": " + title)));
                }
            }
        }
        assertEquals(String.join("\n", offending), List.of(), offending);
    }

    /** A title an api menu reads from the messages a game hands it lives in the game's file. */
    private static Optional<String> title(String key, Map<String, String> own, Map<String, String> api,
                                          List<Map<String, String>> modules) {
        return Optional.ofNullable(own.get(key)).or(() -> Optional.ofNullable(api.get(key)))
                .or(() -> modules.stream().map(messages -> messages.get(key)).filter(value -> value != null).findFirst());
    }
}
