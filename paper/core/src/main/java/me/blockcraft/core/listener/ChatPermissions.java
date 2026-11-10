package me.blockcraft.core.listener;

final class ChatPermissions {

    static final String GLOBAL_MUTE_BYPASS = "blockcraft.globalmute.bypass";
    static final String SPAM_BYPASS = "blockcraft.core.chat.spam.bypass";
    static final String WORDS_BYPASS = "blockcraft.core.chatfilter.bypass.words";
    static final String ADS_BYPASS = "blockcraft.core.chatfilter.bypass.ads";
    static final String FILTER_NOTIFY = "blockcraft.core.chatfilter.notify";
    static final String CHAT_COLOR = "blockcraft.chat.color";
    static final String NICK_SEE = "blockcraft.nick.see";
    /** Whoever may run /team counts as staff. */
    static final String TEAM = "blockcraft.core.team";

    private ChatPermissions() {
    }
}
