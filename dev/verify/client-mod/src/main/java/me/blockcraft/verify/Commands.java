package me.blockcraft.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

import org.lwjgl.sdl.SDLMouse;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class Commands {

    private static final int EVENT_MEMORY = 4000;
    private static final int CHAT_LINES = 30;
    private static final int MOST_FRAMES = 120;

    private static final class Capture {
        private final String name;
        private final int frames;
        private final int every;
        private final JsonArray files = new JsonArray();
        private final CompletableFuture<JsonObject> done;
        private long nextAt;
        private boolean saving;

        private Capture(String name, int frames, int every, CompletableFuture<JsonObject> done) {
            this.name = name;
            this.frames = frames;
            this.every = every;
            this.done = done;
        }
    }

    /** The camera's pose at the end of every client tick: what each rendered frame interpolates between. */
    private record Trace(int ticks, JsonArray samples, CompletableFuture<JsonObject> done) { }

    private final Map<KeyMapping, Integer> held = new HashMap<>();
    private final Map<KeyMapping, Taps> taps = new HashMap<>();

    /** Taps still to come on one key, {@code every} ticks apart. */
    private static final class Taps {
        private int left;
        private final int every;
        private long next;

        private Taps(int left, int every, long next) {
            this.left = left;
            this.every = every;
            this.next = next;
        }
    }
    private final Deque<JsonObject> events = new ArrayDeque<>();
    private final Map<String, String> lastSeen = new HashMap<>();
    private GuiMessage newestChat;
    private long eventsLost;
    private final List<Capture> captures = new ArrayList<>();
    private final List<Trace> traces = new ArrayList<>();
    private boolean prepared;
    private long ticks;

    void tick(Minecraft minecraft) {
        ticks++;
        prepare(minecraft);
        minecraft.getWindow().focused = true;
        if (minecraft.gui.screen() == null && !minecraft.mouseHandler.mouseGrabbed) {
            minecraft.mouseHandler.grabMouse();
        }
        taps.entrySet().removeIf(entry -> {
            var tap = entry.getValue();
            if (tap.next > ticks) {
                return false;
            }
            press(entry.getKey());
            tap.next = ticks + tap.every;
            return --tap.left <= 0;
        });
        held.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue() >= 0 && entry.getValue() <= ticks;
            entry.getKey().isDown = !expired;
            return expired;
        });
        captures.removeIf(capture -> advance(minecraft, capture));
        traces.removeIf(trace -> follow(minecraft, trace));
        observe(minecraft);
    }

    /** Runs every tick here rather than at each poll, so a marker shown for a few ticks is still on record. */
    private void observe(Minecraft minecraft) {
        var hud = minecraft.gui.hud;
        change("actionbar", hud.overlayMessageTime > 0 ? text(hud.overlayMessageString) : "", minecraft);
        change("title", hud.titleTime > 0 ? text(hud.title) : "", minecraft);
        change("subtitle", hud.titleTime > 0 ? text(hud.subtitle) : "", minecraft);
        change("bossbars", String.valueOf(hud.getBossOverlay().events.size()), minecraft);
        change("screen", minecraft.gui.screen() == null ? "" : minecraft.gui.screen().getClass().getSimpleName(), minecraft);
        var camera = minecraft.getCameraEntity();
        change("camera", camera == null ? "" : BuiltInRegistries.ENTITY_TYPE.getKey(camera.getType()).getPath() + " " + camera.getId()
                + (camera.isRemoved() ? " removed" : ""), minecraft);
        if (minecraft.player != null) {
            change("health", String.valueOf(round(minecraft.player.getHealth())), minecraft);
            change("gamemode", minecraft.gameMode == null ? "" : minecraft.gameMode.getPlayerMode().getName(), minecraft);
        }
        var messages = hud.getChat().allMessages;
        var fresh = new ArrayList<String>();
        for (var message : messages) {
            if (message == newestChat) {
                break;
            }
            fresh.add(message.content().getString());
        }
        newestChat = messages.isEmpty() ? null : messages.getFirst();
        fresh.reversed().forEach(line -> record("chat", line));
    }

    private static String text(Component component) {
        return component == null ? "" : component.getString();
    }

    private void change(String kind, String value, Minecraft minecraft) {
        if (!value.equals(lastSeen.put(kind, value))) {
            var entry = record(kind, value);
            if (kind.equals("actionbar") || kind.equals("title") || kind.equals("subtitle")) {
                var hud = minecraft.gui.hud;
                var shown = kind.equals("actionbar") ? hud.overlayMessageString : kind.equals("title") ? hud.title : hud.subtitle;
                entry.addProperty("width", shown == null ? 0 : minecraft.font.width(shown) * (kind.equals("title") ? 4 : kind.equals("subtitle") ? 2 : 1));
            }
        }
    }

    private JsonObject record(String kind, String value) {
        var entry = new JsonObject();
        entry.addProperty("tick", ticks);
        entry.addProperty("kind", kind);
        entry.addProperty("value", value);
        events.addLast(entry);
        if (events.size() > EVENT_MEMORY) {
            events.removeFirst();
            eventsLost = entry.get("tick").getAsLong();
        }
        return entry;
    }

    private void prepare(Minecraft minecraft) {
        if (prepared) {
            return;
        }
        prepared = true;
        var options = minecraft.options;
        options.pauseOnLostFocus = false;
        options.onboardAccessibility = false;
        options.renderDistance().set(6);
        options.framerateLimit().set(30);
        options.tutorialStep = TutorialSteps.NONE;
        options.save();
        minecraft.getSoundManager().addListener((sound, played, range) -> record("sound", sound.getIdentifier().toString()));
    }

    CompletableFuture<JsonObject> run(Minecraft minecraft, JsonObject body) {
        var name = body.get("cmd").getAsString();
        return switch (name) {
            case "status" -> done(status(minecraft));
            case "connect" -> done(connect(minecraft, body.get("host").getAsString()));
            case "disconnect" -> {
                minecraft.disconnectWithProgressScreen();
                minecraft.gui.setScreen(new TitleScreen());
                yield done(new JsonObject());
            }
            case "gui_scale" -> {
                minecraft.options.guiScale().set(body.get("value").getAsInt());
                minecraft.resizeGui();
                yield done(status(minecraft));
            }
            case "window" -> {
                minecraft.getWindow().setWindowed(body.get("width").getAsInt(), body.get("height").getAsInt());
                yield done(status(minecraft));
            }
            case "option" -> done(option(minecraft, body));
            case "look" -> {
                look(minecraft, body.get("yaw").getAsFloat(), body.get("pitch").getAsFloat());
                yield done(status(minecraft));
            }
            case "look_at" -> {
                lookAt(minecraft, new Vec3(body.get("x").getAsDouble(), body.get("y").getAsDouble(), body.get("z").getAsDouble()));
                yield done(status(minecraft));
            }
            case "aim" -> done(aim(minecraft, body));
            case "hold" -> {
                held.put(key(minecraft, body.get("key").getAsString()), body.has("ticks") ? (int) (ticks + body.get("ticks").getAsInt()) : -1);
                yield done(new JsonObject());
            }
            case "release" -> {
                var mapping = key(minecraft, body.get("key").getAsString());
                held.remove(mapping);
                mapping.isDown = false;
                yield done(new JsonObject());
            }
            case "tap" -> {
                var mapping = key(minecraft, body.get("key").getAsString());
                int times = body.has("times") ? body.get("times").getAsInt() : 1;
                press(mapping);
                int every = Math.max(3, body.has("every") ? body.get("every").getAsInt() : 5);
                if (times > 1) {
                    taps.put(mapping, new Taps(times - 1, every, ticks + every));
                }
                yield done(new JsonObject());
            }
            case "hotbar" -> {
                minecraft.player.getInventory().setSelectedSlot(body.get("slot").getAsInt());
                yield done(new JsonObject());
            }
            case "command" -> {
                minecraft.player.connection.sendCommand(body.get("text").getAsString());
                yield done(new JsonObject());
            }
            case "chat" -> {
                minecraft.player.connection.sendChat(body.get("text").getAsString());
                yield done(new JsonObject());
            }
            case "hud" -> done(hud(minecraft));
            case "screen" -> done(screen(minecraft));
            case "click" -> done(click(minecraft, body));
            case "mouse" -> {
                SDLMouse.SDL_WarpMouseInWindow(minecraft.getWindow().handle(), body.get("x").getAsFloat(), body.get("y").getAsFloat());
                yield done(new JsonObject());
            }
            case "press" -> done(press(minecraft, body.get("label").getAsString()));
            case "close" -> {
                minecraft.player.closeContainer();
                minecraft.gui.setScreen(null);
                yield done(new JsonObject());
            }
            case "entities" -> done(entities(minecraft, body));
            case "events" -> done(recorded(body));
            case "screenshot" -> shoot(minecraft, body.get("name").getAsString());
            case "capture" -> capture(body);
            case "trace" -> {
                var result = new CompletableFuture<JsonObject>();
                traces.add(new Trace(Math.clamp(body.get("ticks").getAsInt(), 1, 1200), new JsonArray(), result));
                yield result;
            }
            default -> throw new IllegalArgumentException("Unknown command: " + name);
        };
    }

    private void press(KeyMapping mapping) {
        mapping.clickCount++;
        held.put(mapping, (int) (ticks + 2));
    }

    private static CompletableFuture<JsonObject> done(JsonObject value) {
        return CompletableFuture.completedFuture(value);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private JsonObject status(Minecraft minecraft) {
        var value = new JsonObject();
        var window = minecraft.getWindow();
        value.addProperty("tick", ticks);
        value.addProperty("fps", minecraft.getFps());
        value.addProperty("screen", minecraft.gui.screen() == null ? "" : minecraft.gui.screen().getClass().getSimpleName());
        value.addProperty("window", window.getScreenWidth() + "x" + window.getScreenHeight());
        value.addProperty("gui_scale", window.getGuiScale());
        value.addProperty("gui_size", window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
        var player = minecraft.player;
        value.addProperty("in_world", player != null);
        if (player != null) {
            value.addProperty("x", round(player.getX()));
            value.addProperty("y", round(player.getY()));
            value.addProperty("z", round(player.getZ()));
            value.addProperty("yaw", round(player.getYRot()));
            value.addProperty("pitch", round(player.getXRot()));
            value.addProperty("health", round(player.getHealth()));
            value.addProperty("max_health", round(player.getMaxHealth()));
            value.addProperty("gamemode", minecraft.gameMode == null ? "" : minecraft.gameMode.getPlayerMode().getName());
            value.addProperty("in_wall", player.isInWall());
            value.addProperty("vehicle", player.getVehicle() == null ? "" : player.getVehicle().getType().toShortString());
            value.addProperty("slot", player.getInventory().getSelectedSlot());
            var hotbar = new JsonArray();
            for (int slot = 0; slot < 9; slot++) {
                hotbar.add(describe(player.getInventory().getItem(slot)));
            }
            value.add("hotbar", hotbar);
            value.add("offhand", describe(player.getOffhandItem()));
            if (minecraft.hitResult instanceof EntityHitResult hit) {
                value.addProperty("looking_at", name(hit.getEntity()));
            }
            value.addProperty("dimension", minecraft.level.dimension().identifier().toString());
            var border = minecraft.level.getWorldBorder();
            var edge = new JsonObject();
            edge.addProperty("x", round(border.getCenterX()));
            edge.addProperty("z", round(border.getCenterZ()));
            edge.addProperty("size", round(border.getSize()));
            edge.addProperty("distance", round(border.getDistanceToBorder(player)));
            value.add("border", edge);
        }
        return value;
    }

    private JsonObject connect(Minecraft minecraft, String host) {
        var server = new ServerData("verify", host, ServerData.Type.OTHER);
        server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(host), server, false, null);
        return new JsonObject();
    }

    private JsonObject option(Minecraft minecraft, JsonObject body) {
        var options = minecraft.options;
        if (body.has("fov")) {
            options.fov().set(body.get("fov").getAsInt());
        }
        if (body.has("fov_effects")) {
            options.fovEffectScale().set(body.get("fov_effects").getAsDouble());
        }
        if (body.has("render_distance")) {
            options.renderDistance().set(body.get("render_distance").getAsInt());
        }
        if (body.has("particles")) {
            options.particles().set(ParticleStatus.valueOf(body.get("particles").getAsString().toUpperCase()));
        }
        if (body.has("perspective")) {
            options.setCameraType(CameraType.valueOf(body.get("perspective").getAsString().toUpperCase()));
        }
        if (body.has("hide_hud") && body.get("hide_hud").getAsBoolean() != minecraft.gui.hud.isHidden()) {
            minecraft.gui.hud.toggle();
        }
        return status(minecraft);
    }

    private static void look(Minecraft minecraft, float yaw, float pitch) {
        minecraft.player.setYRot(yaw);
        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setXRot(pitch);
    }

    private static void lookAt(Minecraft minecraft, Vec3 target) {
        var eye = minecraft.player.getEyePosition();
        var along = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-along.x, along.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(along.y, Math.hypot(along.x, along.z)));
        look(minecraft, yaw, pitch);
    }

    /** Turns to the nearest living entity whose description contains {@code name}, or any but the player. */
    private JsonObject aim(Minecraft minecraft, JsonObject body) {
        var wanted = body.has("name") ? body.get("name").getAsString().toLowerCase() : "";
        var player = minecraft.player;
        var value = new JsonObject();
        var target = stream(minecraft).filter(entity -> entity != player && entity instanceof LivingEntity living && living.isAlive())
                .filter(entity -> name(entity).toLowerCase().contains(wanted))
                .min(Comparator.comparingDouble(player::distanceToSqr));
        target.ifPresent(entity -> {
            lookAt(minecraft, entity.getBoundingBox().getCenter());
            value.addProperty("aimed_at", name(entity));
            value.addProperty("distance", round(player.distanceTo(entity)));
        });
        return value;
    }

    private static Stream<Entity> stream(Minecraft minecraft) {
        var all = new ArrayList<Entity>();
        minecraft.level.entitiesForRendering().forEach(all::add);
        return all.stream();
    }

    private static String name(Entity entity) {
        var custom = entity.getCustomName();
        return entity.getType().toShortString() + (custom == null ? "" : " " + custom.getString());
    }

    private KeyMapping key(Minecraft minecraft, String name) {
        var options = minecraft.options;
        return switch (name) {
            case "forward" -> options.keyUp;
            case "back" -> options.keyDown;
            case "left" -> options.keyLeft;
            case "right" -> options.keyRight;
            case "jump" -> options.keyJump;
            case "sneak" -> options.keyShift;
            case "sprint" -> options.keySprint;
            case "attack" -> options.keyAttack;
            case "use" -> options.keyUse;
            case "swap" -> options.keySwapOffhand;
            case "drop" -> options.keyDrop;
            case "inventory" -> options.keyInventory;
            default -> throw new IllegalArgumentException("Unknown key: " + name);
        };
    }

    /** Text as the server sent it, with how wide the client's font draws it, so an overflow is a number and not a guess. */
    private JsonObject hud(Minecraft minecraft) {
        var hud = minecraft.gui.hud;
        var font = minecraft.font;
        var window = minecraft.getWindow();
        int guiWidth = window.getGuiScaledWidth();
        var value = new JsonObject();
        value.addProperty("gui_width", guiWidth);
        value.addProperty("gui_height", window.getGuiScaledHeight());
        value.add("actionbar", measured(font, hud.overlayMessageTime > 0 ? hud.overlayMessageString : null, 1, guiWidth));
        value.add("title", measured(font, hud.titleTime > 0 ? hud.title : null, 4, guiWidth));
        value.add("subtitle", measured(font, hud.titleTime > 0 ? hud.subtitle : null, 2, guiWidth));
        var bars = new JsonArray();
        hud.getBossOverlay().events.values().forEach(bar -> {
            var entry = measured(font, bar.getName(), 1, guiWidth);
            entry.addProperty("progress", round(bar.getProgress()));
            entry.addProperty("color", bar.getColor().name());
            bars.add(entry);
        });
        value.add("bossbars", bars);
        value.add("sidebar", sidebar(minecraft, font, guiWidth));
        var chat = new JsonArray();
        var messages = hud.getChat().allMessages;
        messages.stream().limit(CHAT_LINES).toList().reversed().forEach(message -> chat.add(message.content().getString()));
        value.add("chat", chat);
        return value;
    }

    private static JsonObject measured(Font font, Component text, int scale, int guiWidth) {
        var value = new JsonObject();
        value.addProperty("text", text == null ? "" : text.getString());
        int width = text == null ? 0 : font.width(text) * scale;
        value.addProperty("width", width);
        value.addProperty("overflows", width > guiWidth);
        return value;
    }

    private static JsonObject sidebar(Minecraft minecraft, Font font, int guiWidth) {
        var value = new JsonObject();
        var lines = new JsonArray();
        int widest = 0;
        if (minecraft.level != null) {
            var board = minecraft.level.getScoreboard();
            var objective = board.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
                value.addProperty("title", objective.getDisplayName().getString());
                widest = font.width(objective.getDisplayName());
                var entries = new ArrayList<>(board.listPlayerScores(objective));
                entries.sort(Comparator.comparingInt((net.minecraft.world.scores.PlayerScoreEntry entry) -> -entry.value()));
                for (var entry : entries) {
                    var team = board.getPlayersTeam(entry.owner());
                    var line = PlayerTeam.formatNameForTeam(team, entry.ownerName());
                    lines.add(line.getString());
                    widest = Math.max(widest, font.width(line));
                }
            }
        }
        value.add("lines", lines);
        value.addProperty("width", widest);
        value.addProperty("share_of_width", round((double) widest / guiWidth));
        return value;
    }

    private JsonObject screen(Minecraft minecraft) {
        var value = new JsonObject();
        var screen = minecraft.gui.screen();
        value.addProperty("screen", screen == null ? "" : screen.getClass().getSimpleName());
        if (screen != null) {
            value.addProperty("title", screen.getTitle().getString());
            var labels = new JsonArray();
            buttons(screen).forEach(button -> labels.add(button.getMessage().getString()));
            value.add("buttons", labels);
        }
        if (screen instanceof AbstractContainerScreen<?> container) {
            var slots = new JsonArray();
            for (var slot : container.getMenu().slots) {
                if (slot.hasItem() && slot.container != minecraft.player.getInventory()) {
                    var entry = describe(slot.getItem());
                    entry.addProperty("slot", slot.index);
                    slots.add(entry);
                }
            }
            value.add("slots", slots);
        }
        return value;
    }

    private static JsonObject press(Minecraft minecraft, String label) {
        var screen = minecraft.gui.screen();
        if (screen == null) {
            throw new IllegalStateException("No screen is open");
        }
        var button = buttons(screen).filter(each -> each.getMessage().getString().contains(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No button says " + label));
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        return new JsonObject();
    }

    /** Every button on the screen, also those a dialog nests in its scrolling body. */
    private static Stream<AbstractButton> buttons(Screen screen) {
        return flatten(screen).filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast);
    }

    private static Stream<GuiEventListener> flatten(ContainerEventHandler container) {
        return container.children().stream().flatMap(child -> child instanceof ContainerEventHandler nested
                ? Stream.concat(Stream.of(child), flatten(nested)) : Stream.of(child));
    }

    private JsonObject click(Minecraft minecraft, JsonObject body) {
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> container)) {
            throw new IllegalStateException("No container is open");
        }
        minecraft.gameMode.handleContainerInput(container.getMenu().containerId, body.get("slot").getAsInt(),
                body.has("button") ? body.get("button").getAsInt() : 0, ContainerInput.PICKUP, minecraft.player);
        return new JsonObject();
    }

    private static JsonObject describe(ItemStack stack) {
        var value = new JsonObject();
        value.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        if (!stack.isEmpty()) {
            value.addProperty("name", stack.getHoverName().getString());
            value.addProperty("count", stack.getCount());
            var lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                var lines = new JsonArray();
                lore.lines().forEach(line -> lines.add(line.getString()));
                value.add("lore", lines);
            }
        }
        return value;
    }

    private JsonObject entities(Minecraft minecraft, JsonObject body) {
        double radius = body.has("radius") ? body.get("radius").getAsDouble() : 32;
        var value = new JsonObject();
        var list = new JsonArray();
        if (minecraft.level == null) {
            value.add("entities", list);
            return value;
        }
        var counts = new HashMap<String, Integer>();
        var player = minecraft.player;
        stream(minecraft).filter(entity -> entity != player && player.distanceTo(entity) <= radius)
                .sorted(Comparator.comparingDouble(player::distanceToSqr)).forEach(entity -> {
                    counts.merge(entity.getType().toShortString(), 1, Integer::sum);
                    var entry = new JsonObject();
                    entry.addProperty("name", name(entity));
                    entry.addProperty("distance", round(player.distanceTo(entity)));
                    entry.addProperty("x", round(entity.getX()));
                    entry.addProperty("y", round(entity.getY()));
                    entry.addProperty("z", round(entity.getZ()));
                    entry.addProperty("glowing", entity.isCurrentlyGlowing());
                    entry.addProperty("in_sight", player.hasLineOfSight(entity));
                    if (entity instanceof LivingEntity living) {
                        entry.addProperty("health", round(living.getHealth()));
                    }
                    if (entity instanceof Display.ItemDisplay display) {
                        var model = display.getSlot(0).get().get(DataComponents.ITEM_MODEL);
                        entry.addProperty("model", model == null ? "" : model.toString());
                    }
                    list.add(entry);
                });
        var totals = new JsonObject();
        counts.forEach(totals::addProperty);
        value.add("totals", totals);
        if (!body.has("totals_only")) {
            value.add("entities", list);
        }
        return value;
    }

    private JsonObject recorded(JsonObject body) {
        long since = body.has("since") ? body.get("since").getAsLong() : 0;
        var value = new JsonObject();
        var list = new JsonArray();
        events.stream().filter(event -> event.get("tick").getAsLong() >= since).forEach(list::add);
        value.add("events", list);
        value.addProperty("tick", ticks);
        value.addProperty("history_lost", since <= eventsLost && eventsLost > 0);
        return value;
    }

    private CompletableFuture<JsonObject> shoot(Minecraft minecraft, String name) {
        var result = new CompletableFuture<JsonObject>();
        grab(minecraft, name, file -> {
            var value = new JsonObject();
            value.addProperty("file", file);
            value.addProperty("tick", ticks);
            result.complete(value);
        }, result::completeExceptionally);
        return result;
    }

    private static void grab(Minecraft minecraft, String name, Consumer<String> saved, Consumer<Throwable> failed) {
        var relative = name + ".png";
        var file = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(relative);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException failure) {
            failed.accept(failure);
            return;
        }
        Screenshot.grab(minecraft.gameDirectory, relative, minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
            if (Files.isRegularFile(file)) {
                saved.accept("screenshots/" + relative);
            } else {
                failed.accept(new IOException(message.getString()));
            }
        });
    }

    private CompletableFuture<JsonObject> capture(JsonObject body) {
        var result = new CompletableFuture<JsonObject>();
        int frames = Math.clamp(body.get("frames").getAsInt(), 1, MOST_FRAMES);
        int every = Math.max(1, body.has("every") ? body.get("every").getAsInt() : 2);
        captures.add(new Capture(body.get("name").getAsString(), frames, every, result));
        return result;
    }

    /** One frame at a time, the next only once the last is on disk; true once the last is saved or one failed. */
    private static boolean follow(Minecraft minecraft, Trace trace) {
        var camera = minecraft.getCameraEntity();
        var sample = new JsonArray();
        if (camera != null) {
            sample.add(camera.getX());
            sample.add(camera.getY());
            sample.add(camera.getZ());
            sample.add(camera.getYRot());
            sample.add(camera.getXRot());
            sample.add(camera == minecraft.player ? "player" : BuiltInRegistries.ENTITY_TYPE.getKey(camera.getType()).getPath());
        }
        trace.samples().add(sample);
        if (trace.samples().size() < trace.ticks()) {
            return false;
        }
        var value = new JsonObject();
        value.add("samples", trace.samples());
        trace.done().complete(value);
        return true;
    }

    private boolean advance(Minecraft minecraft, Capture capture) {
        if (capture.done.isDone()) {
            return true;
        }
        if (capture.files.size() >= capture.frames) {
            var value = new JsonObject();
            value.add("files", capture.files);
            capture.done.complete(value);
            return true;
        }
        if (!capture.saving && ticks >= capture.nextAt) {
            capture.saving = true;
            capture.nextAt = ticks + capture.every;
            grab(minecraft, capture.name + "_" + String.format("%03d", capture.files.size()), file -> {
                capture.files.add(file);
                capture.saving = false;
            }, capture.done::completeExceptionally);
        }
        return false;
    }
}
