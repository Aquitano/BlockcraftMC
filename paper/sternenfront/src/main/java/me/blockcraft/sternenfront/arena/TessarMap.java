package me.blockcraft.sternenfront.arena;

import me.blockcraft.sternenfront.arena.FrontMap.Headquarters;
import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Wrack von Tessar, built into an empty world at every start: a desert basin between canyon walls, the Kolonien
 * camp with its freighter in the west, the Hegemonie landing platform in the east, and between them on the
 * north-south centre line the Raffinerie (A), the crashed cruiser (B) and the Basar (C), with the temple ruin of
 * the relic in the northern cliff behind A, and the secrets {@link SecretPlaces} hides. Uses Bukkit only, so a bare
 * Paper server can build it for a look without the network around it. The same seed builds the same map every time.
 */
public final class TessarMap {

    public static final String NAME = "Wrack von Tessar";

    static final int FLOOR = 64;
    static final int POST_A = -44;
    static final int POST_C = 44;
    static final int HEADQUARTERS = 82;
    private static final long SEED = 0x7E55A2L;

    private final Canvas canvas;

    private TessarMap(World world) {
        this.canvas = new Canvas(world);
    }

    public static FrontMap build(World world) {
        return new TessarMap(world).build();
    }

    private FrontMap build() {
        var world = canvas.world();
        var terrain = new Terrain(canvas, SEED, List.of(
                new Terrain.Flat(0, POST_A, 16, 8), new Terrain.Flat(0, 0, 14, 8), new Terrain.Flat(0, POST_C, 18, 8),
                new Terrain.Flat(0, -26, 12, 6), new Terrain.Flat(0, 26, 12, 6),
                new Terrain.Flat(-HEADQUARTERS, 0, 26, 8), new Terrain.Flat(HEADQUARTERS, 0, 26, 8),
                new Terrain.Flat(-34, 7, 8, 5), new Terrain.Flat(34, -7, 8, 5), new Terrain.Flat(-58, 41, 11, 5), new Terrain.Flat(58, -41, 11, 5),
                new Terrain.Flat(-55, -1, 12, 5), new Terrain.Flat(55, -1, 12, 5), new Terrain.Flat(-38, -46, 9, 4), new Terrain.Flat(38, -46, 9, 4),
                new Terrain.Flat(-49, -43, 6, 4), new Terrain.Flat(49, -43, 6, 4)));
        terrain.build();
        var random = new Random(SEED);
        new Wreck(canvas, terrain, random).build();
        new Refinery(canvas, random).build();
        var temple = new TempleRuin(canvas, random);
        temple.build();
        new Bazaar(canvas, random).build();
        new Bases(canvas, terrain, random).build();
        new Props(canvas, terrain, random).build();
        var secrets = new SecretPlaces(canvas, terrain).build();
        terrain.plant();
        terrain.settle();
        terrain.rim();
        canvas.join();
        terrain.desert();
        var posts = new Posts(canvas, terrain);
        var postA = posts.post("raffinerie", 0, POST_A, 6.5, List.of(spot(-5, -53, 0), spot(5, -53, 0), spot(-7, -35, 180), spot(7, -35, 180)));
        var postB = posts.post("wrack", 0, 0, 7, List.of(spot(-5, -6, 0), spot(5, 6, 180), spot(-5, 6, 180), spot(5, -6, 0)));
        var postC = posts.post("basar", 0, POST_C, 6.5, List.of(spot(-10, 43, 270), spot(10, 43, 90), spot(-4, 54, 180), spot(4, 54, 180)));
        var controls = TessarControls.build(canvas);
        var headquarters = Map.of(
                Faction.KOLONIEN, headquarters(-1),
                Faction.HEGEMONIE, headquarters(1));
        var lobby = lobby();
        return new FrontMap(NAME, world,
                new BoundingBox(Terrain.MIN_X, Terrain.BASE, Terrain.MIN_Z, Terrain.MAX_X + 1, FLOOR + 64, Terrain.MAX_Z + 1),
                headquarters, List.of(postA, postB, postC), temple.relic(world),
                at(0, FLOOR + 57, 60, 180, 50), lobby, at(0, FLOOR + 30, 30, 180, 35), secrets, controls);
    }

    private Headquarters headquarters(int side) {
        float yaw = side < 0 ? 270 : 90;
        var spawns = List.of(spot(side * HEADQUARTERS, -6, yaw), spot(side * HEADQUARTERS, -2, yaw),
                spot(side * HEADQUARTERS, 2, yaw), spot(side * HEADQUARTERS, 6, yaw));
        int near = side * 63;
        int far = side * (Terrain.MAX_X + 1);
        return new Headquarters(spawns, new BoundingBox(Math.min(near, far), FLOOR - 4, -30, Math.max(near, far), FLOOR + 40, 31));
    }

    private Location lobby() {
        int y = FLOOR + 54;
        canvas.fill(-34, y, -66, -26, y, -58, Material.SMOOTH_QUARTZ);
        for (int x = -34; x <= -26; x++) {
            for (int z = -66; z <= -58; z++) {
                if (x == -34 || x == -26 || z == -66 || z == -58) {
                    canvas.set(x, y, z, Material.LIGHT_GRAY_CONCRETE);
                    canvas.fill(x, y + 1, z, x, y + 3, z, Material.BARRIER);
                }
            }
        }
        canvas.set(-30, y, -62, Material.SEA_LANTERN);
        return at(-30, y + 1, -62, 180, 10);
    }

    private Location spot(int x, int z, float yaw) {
        return at(x, FLOOR + 1, z, yaw, 0);
    }

    private Location at(double x, double y, double z, float yaw, float pitch) {
        return new Location(canvas.world(), x + 0.5, y, z + 0.5, yaw, pitch);
    }
}
