package me.blockcraft.api.rank;

import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TabTeamsTest {

    @Test
    public void rankGroupingDoesNotPreventOpponentsFromHittingEachOther() throws Exception {
        var friendlyFire = new AtomicBoolean(true);
        var names = new HashSet<String>();
        var team = (Team) Proxy.newProxyInstance(Team.class.getClassLoader(), new Class<?>[]{Team.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setAllowFriendlyFire" -> {
                        friendlyFire.set((boolean) args[0]);
                        yield null;
                    }
                    case "addEntry" -> names.add((String) args[0]);
                    case "prefix", "suffix", "setCanSeeFriendlyInvisibles" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        var exists = new AtomicBoolean();
        var board = (Scoreboard) Proxy.newProxyInstance(Scoreboard.class.getClassLoader(), new Class<?>[]{Scoreboard.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTeam" -> exists.get() ? team : null;
                    case "registerNewTeam" -> {
                        exists.set(true);
                        yield team;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var entryType = Class.forName(TabTeams.class.getName() + "$Entry");
        var constructor = entryType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var apply = TabTeams.class.getDeclaredMethod("apply", Scoreboard.class, entryType);
        apply.setAccessible(true);
        for (String name : new String[]{"Red", "Blue"}) {
            var entry = constructor.newInstance("9999default--", Component.empty(), Component.empty(), Optional.empty(), name);
            apply.invoke(null, board, entry);
            assertTrue("Tab-list groups must allow combat", friendlyFire.get());
            friendlyFire.set(false);
        }
        assertEquals(2, names.size());
    }
}
