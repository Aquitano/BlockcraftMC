package me.blockcraft.story.quest;

import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.Voices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.conversation.Conversation;
import org.betonquest.betonquest.conversation.ConversationIO;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * BetonQuest's conversation output through the {@link DialogueHud}, as the conversation IO {@code blockhud}. BetonQuest
 * ends a conversation as soon as it has shown a line without answers, so that end waits until the player has read it;
 * any other end, such as walking away mid-question, closes the panel at once.
 * A line's voice is the NPC option it came from, {@code <conversation>.<option>}, found by its text.
 */
final class HudConversationIO implements ConversationIO {

    static final String NAME = "blockhud";

    private final Plugin plugin;
    private final Conversation conversation;
    private final OnlineProfile profile;
    private final DialogueHud hud;
    private final Voices voices;
    private final List<String> answers = new ArrayList<>();
    private Component speaker = Component.empty();
    private Component text = Component.empty();
    private Runnable ended;
    private boolean read;

    HudConversationIO(Plugin plugin, Conversation conversation, OnlineProfile profile, DialogueHud hud, Voices voices) {
        this.plugin = plugin;
        this.conversation = conversation;
        this.profile = profile;
        this.hud = hud;
        this.voices = voices;
    }

    @Override
    public void begin() {
    }

    @Override
    public void setNpcResponse(Component npcName, Component response) {
        speaker = npcName;
        text = response;
    }

    @Override
    public void addPlayerOption(Component option, ConfigurationSection properties) {
        answers.add(plain(option));
    }

    @Override
    public void display() {
        var player = profile.getPlayer();
        var line = plain(text);
        if (line.isBlank() && answers.isEmpty()) {
            conversation.endConversation();
            return;
        }
        read = false;
        hud.say(player, new DialogueHud.Line(plain(speaker), line, voiceOf(line)), List.copyOf(answers), new DialogueHud.Reply() {
            @Override
            public void chosen(int index) {
                conversation.passPlayerAnswer(index + 1);
            }

            @Override
            public void read() {
                HudConversationIO.this.read = true;
                if (ended != null) finish();
            }

            @Override
            public void closed() {
                conversation.endConversation();
            }
        });
    }

    private Optional<Voices.Voice> voiceOf(String line) {
        var name = conversation.getID().get();
        var options = conversation.getPackage().getConfig().getConfigurationSection("conversations." + name + ".NPC_options");
        if (options == null) return Optional.empty();
        return options.getKeys(false).stream()
                .filter(option -> line.equals(MiniMessage.miniMessage().stripTags(options.getString(option + ".text", "")).trim()))
                .findFirst().flatMap(option -> voices.of(name + "." + option));
    }

    @Override
    public void clear() {
        answers.clear();
        speaker = Component.empty();
        text = Component.empty();
    }

    @Override
    public void end(Runnable callback) {
        if (!plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> end(callback));
            return;
        }
        ended = callback;
        if (read || !answers.isEmpty() || !hud.showing(profile.getPlayer())) finish();
    }

    private void finish() {
        var callback = ended;
        ended = null;
        hud.close(profile.getPlayer());
        callback.run();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).trim();
    }
}
