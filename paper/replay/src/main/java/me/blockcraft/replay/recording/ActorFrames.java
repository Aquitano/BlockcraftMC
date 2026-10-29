package me.blockcraft.replay.recording;

import me.blockcraft.replay.data.ReplayEvent;

import java.util.Arrays;
import java.util.List;

final class ActorFrames {

    private ActorFrames() { }

    static boolean same(List<ReplayEvent.Actor> first, List<ReplayEvent.Actor> second) {
        if (first.size() != second.size()) { return false; }
        for (int index = 0; index < first.size(); index++) {
            var a = first.get(index);
            var b = second.get(index);
            if (!a.id().equals(b.id()) || !a.world().equals(b.world()) || a.x() != b.x() || a.y() != b.y()
                    || a.z() != b.z() || a.yaw() != b.yaw() || a.pitch() != b.pitch() || !a.pose().equals(b.pose())
                    || a.equipment().size() != b.equipment().size()) { return false; }
            for (int slot = 0; slot < a.equipment().size(); slot++) {
                if (!Arrays.equals(a.equipment().get(slot), b.equipment().get(slot))) { return false; }
            }
        }
        return true;
    }
}
