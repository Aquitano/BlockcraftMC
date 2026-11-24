package me.blockcraft.lobby.team;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.sign.SignSide;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class TeamBoardTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Map<Integer, Block> blocks = new HashMap<>();
    private final Map<Integer, List<Component>> signs = new HashMap<>();
    private final Map<Integer, String> skins = new HashMap<>();
    private Field serverField;
    private Object previousServer;
    private World world;
    private Path source;
    private Path active;
    private YamlConfiguration yaml;
    private TeamBoard board;
    private int updates;

    @Before
    public void setUp() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        var uuid = UUID.randomUUID();
        world = proxy(World.class, (object, method, args) -> switch (method.getName()) {
            case "getName" -> "world";
            case "getUID" -> uuid;
            case "key" -> Key.key("minecraft", "overworld");
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getBlockAt" -> blocks.get(args[0] instanceof Location point ? point.getBlockX() : (int) args[0]);
            case "equals" -> object == args[0];
            case "hashCode" -> System.identityHashCode(object);
            default -> throw new AssertionError(method);
        });
        var server = proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getWorld" -> args[0].equals("world") || args[0].equals(uuid)
                    || args[0].equals(Key.key("minecraft", "overworld")) ? world : null;
            default -> throw new AssertionError(method);
        });
        serverField.set(null, server);
        source = folder.newFile("team-board.yml").toPath();
        active = folder.newFile("active.yml").toPath();
        Files.writeString(active, "unchanged");
        yaml = new YamlConfiguration();
        yaml.set("title", sign(0));
        yaml.set("empty-skin", "MHF_Question");
        yaml.set("slots.first.head", head(1));
        yaml.set("slots.first.sign", sign(2));
        yaml.set("slots.second.head", head(3));
        yaml.set("slots.second.sign", sign(4));
        yaml.set("members.first.name", "Aquitano");
        yaml.set("members.first.role", "Owner");
        yaml.save(source.toFile());
        var messages = Messages.load(folder.newFolder("messages").toPath(),
                () -> getClass().getResourceAsStream("/messages.yml"), Placeholder.unparsed("ip", "example.test"),
                Placeholder.parsed("primary", "<blue>"), Placeholder.parsed("accent", "<yellow>"),
                Placeholder.parsed("dark", "<dark_gray>"), Placeholder.parsed("muted", "<gray>"));
        board = new TeamBoard(source, active, messages, name -> proxy(ResolvableProfile.class,
                (profile, method, args) -> {
                    if (method.getName().equals("name")) return name;
                    throw new AssertionError(method);
                }));
    }

    @After
    public void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test
    public void checkReportsTheLayoutWithoutChangingTheWallOrActiveConfiguration() throws Exception {
        var result = board.refresh(false);
        assertEquals(new TeamBoard.Result(1, 2, List.of()), result);
        assertEquals(0, updates);
        assertTrue(skins.isEmpty());
        assertEquals("unchanged", Files.readString(active));
    }

    @Test
    public void applyRefreshesMembersEmptySlotsAndBrandingAndKeepsTheConfigurationWithTheMap() throws Exception {
        assertTrue(board.refresh(true).problems().isEmpty());
        assertEquals(Map.of(1, "Aquitano", 3, "MHF_Question"), skins);
        assertEquals("example.test", plain(signs.get(0).get(1)));
        assertEquals("Owner", plain(signs.get(2).get(1)));
        assertEquals("Aquitano", plain(signs.get(2).get(2)));
        assertEquals("Freier Platz", plain(signs.get(4).get(1)));
        assertEquals(5, updates);
        assertEquals(Files.readString(source), Files.readString(active));
    }

    @Test
    public void aBadRosterOrOverlappingPositionLeavesEverySlotUntouched() throws Exception {
        yaml.set("members.second.name", "invalid name");
        yaml.set("members.second.role", "Moderator");
        yaml.set("slots.second.sign", new Location(world, 2, 64, 0, 90, 45));
        yaml.save(source.toFile());
        var result = board.refresh(true);
        assertTrue(result.problems().contains("members.second"));
        assertTrue(result.problems().contains("slots.second.sign"));
        assertEquals(0, updates);
        assertTrue(skins.isEmpty());
        assertEquals("unchanged", Files.readString(active));
    }

    private Location head(int x) {
        var state = proxy(Skull.class, (object, method, args) -> switch (method.getName()) {
            case "setProfile" -> skins.put(x, ((ResolvableProfile) args[0]).name());
            case "update" -> { updates++; yield true; }
            default -> throw new AssertionError(method);
        });
        blocks.put(x, block(Material.PLAYER_HEAD, state));
        return new Location(world, x, 64, 0);
    }

    private Location sign(int x) {
        var lines = new ArrayList<Component>(List.of(Component.empty(), Component.empty(), Component.empty(), Component.empty()));
        signs.put(x, lines);
        var side = proxy(SignSide.class, (object, method, args) -> {
            if (method.getName().equals("line") && args.length == 2) {
                lines.set((int) args[0], (Component) args[1]);
                return null;
            }
            throw new AssertionError(method);
        });
        var state = proxy(Sign.class, (object, method, args) -> switch (method.getName()) {
            case "getSide" -> side;
            case "update" -> { updates++; yield true; }
            default -> throw new AssertionError(method);
        });
        blocks.put(x, block(Material.OAK_WALL_SIGN, state));
        return new Location(world, x, 64, 0);
    }

    private static Block block(Material material, BlockState state) {
        return proxy(Block.class, (object, method, args) -> switch (method.getName()) {
            case "getType" -> material;
            case "getState" -> state;
            default -> throw new AssertionError(method);
        });
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
