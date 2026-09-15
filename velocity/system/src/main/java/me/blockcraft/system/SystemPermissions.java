package me.blockcraft.system;

public final class SystemPermissions {

    private static final String ROOT = "blockcraft.system.";

    public static final String BAN = ROOT + "ban";
    public static final String MUTE = ROOT + "mute";
    public static final String BAN_LIST = ROOT + "banlist";
    public static final String MUTE_LIST = ROOT + "mutelist";
    public static final String CHECK = ROOT + "check";
    public static final String HISTORY = ROOT + "history";
    public static final String KICK = ROOT + "kick";
    public static final String STRIKE = ROOT + "strike";
    public static final String STRIKES = ROOT + "strikes";
    public static final String WARN = ROOT + "warn";
    public static final String WARNS = ROOT + "warns";
    public static final String WERBUNG = ROOT + "werbung";
    public static final String TEAM_CHAT = ROOT + "tc";
    public static final String TEAM = ROOT + "team";
    public static final String FIND = ROOT + "find";
    public static final String GOTO = ROOT + "goto";
    public static final String CLEAR_CHAT = ROOT + "cc";
    public static final String GLOBAL_MUTE = ROOT + "globalmute";
    public static final String BROADCAST = ROOT + "bc";
    public static final String MAINTENANCE = ROOT + "wartungen";
    public static final String MAINTENANCE_JOIN = ROOT + "wartungen.join";
    public static final String RESTART = ROOT + "restart";
    public static final String ADD_PREMIUM = ROOT + "addpremium";
    public static final String SET_MAX = ROOT + "setmax";
    public static final String MOTD = ROOT + "motd";
    public static final String JOIN_ME = ROOT + "joinme";
    public static final String MSG = ROOT + "msg";
    public static final String MSG_BYPASS = ROOT + "msg.bypass";
    public static final String FRIENDS = ROOT + "friends";
    public static final String DISCORD = ROOT + "discord";
    public static final String JOIN_ME_NO_DELAY = ROOT + "joinme.nodelay";
    public static final String JOIN_ME_CLEAR = ROOT + "joinme.clear";
    public static final String SUPPORT_STAFF = ROOT + "support.staff";
    public static final String PARTY_INVITE = ROOT + "party.invite";
    public static final String PARTY_KICK = ROOT + "party.kick";
    public static final String FULL_JOIN = ROOT + "fulljoin";
    public static final String BLOCKED_COMMANDS_BYPASS = ROOT + "blockedcommands.bypass";
    public static final String TAB_COMPLETE = ROOT + "tab";
    public static final String BYPASS_RANK = ROOT + "bypass-rank";
    public static final String PREMIUM_GROUP = "group.premium";

    /** The Paper side's nodes for looking through vanish and nicks; one node covers both sides. */
    public static final String SEE_VANISHED = "blockcraft.core.vanish.see";
    public static final String SEE_NICKS = "blockcraft.nick.see";

    public static final String SEEN = ROOT + "seen";
    public static final String PLAYTIME = ROOT + "playtime";
    public static final String PLAYTIME_OTHERS = PLAYTIME + ".others";

    private SystemPermissions() {
    }
}
