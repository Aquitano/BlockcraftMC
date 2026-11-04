package me.blockcraft.core.staff;

import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StaffToolTest {

    @Test
    public void toolsRequireTheirCommandPermissionsAndLeavingRemainsAvailable() {
        var permissions = new HashSet<>(Set.of("blockcraft.core.staff"));
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        return permissions.contains(args[0]);
                    }
                    throw new AssertionError(method.getName());
                });
        assertTrue(StaffTool.SPECTATE.permitted(player));
        assertFalse(StaffTool.FREEZE.permitted(player));
        assertFalse(StaffTool.INVSEE.permitted(player));
        assertFalse(StaffTool.REPORTS.permitted(player));
        permissions.addAll(Set.of("blockcraft.core.freeze", "blockcraft.core.invsee", "blockcraft.reports.menu"));
        assertTrue(StaffTool.FREEZE.permitted(player));
        assertTrue(StaffTool.INVSEE.permitted(player));
        assertTrue(StaffTool.REPORTS.permitted(player));
        permissions.clear();
        assertTrue(StaffTool.LEAVE.permitted(player));
        assertFalse(StaffTool.SPECTATE.permitted(player));
    }
}
