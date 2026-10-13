package me.blockcraft.common.network;

import me.blockcraft.common.nick.Nick;
import me.blockcraft.common.settings.PlayerSetting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The versioned wire format of {@link ProxyMessage}: a version byte, a type byte, then the record's fields. */
public final class ProxyMessageCodec {

    public static final String CHANNEL = "blockcraft:main";

    private static final int VERSION = 1;
    private static final int FRIEND_AGES_VERSION = 2;
    private static final int MAX_BYTES = 32_766;
    private static final int MAX_COUNT = 4096;

    private static final int PLAY = 1;
    private static final int HUB = 2;
    private static final int COMMAND = 3;
    private static final int NICK = 4;
    private static final int VANISH = 5;
    private static final int AFK = 6;
    private static final int STAFF_NOTICE = 7;
    private static final int SETTING = 8;
    private static final int PACK_REQUEST = 9;
    private static final int FRIENDS_REQUEST = 10;
    private static final int FRIENDS_REPLY = 11;
    private static final int PACK = 12;
    private static final int SPECTATE = 13;
    private static final int IGNORE = 14;
    private static final int WELCOME = 15;
    private static final int ANNOUNCEMENT = 16;
    private static final int REMATCH = 17;
    private static final int NEWCOMER = 18;

    private ProxyMessageCodec() {
    }

    /** Throws {@link IllegalArgumentException} when the message does not fit into one plugin message. */
    public static byte[] encode(ProxyMessage message) {
        int version = switch (message) {
            case ProxyMessage.FriendsReply reply when reply.friends().stream().anyMatch(friend -> friend.days() >= 0) -> FRIEND_AGES_VERSION;
            default -> VERSION;
        };
        var bytes = write(out -> {
            out.writeByte(version);
            switch (message) {
                case ProxyMessage.Play play -> {
                    out.writeByte(PLAY);
                    out.writeUTF(play.group());
                }
                case ProxyMessage.Hub _ -> out.writeByte(HUB);
                case ProxyMessage.Command command -> {
                    out.writeByte(COMMAND);
                    out.writeUTF(command.line());
                }
                case ProxyMessage.NickChange change -> {
                    out.writeByte(NICK);
                    out.writeBoolean(change.nick().isPresent());
                    if (change.nick().isPresent()) {
                        var nick = change.nick().get();
                        out.writeUTF(nick.name());
                        out.writeUTF(nick.skinOwner());
                        out.writeUTF(nick.rankGroup());
                    }
                }
                case ProxyMessage.Vanish vanish -> {
                    out.writeByte(VANISH);
                    out.writeBoolean(vanish.vanished());
                }
                case ProxyMessage.Afk afk -> {
                    out.writeByte(AFK);
                    out.writeBoolean(afk.afk());
                }
                case ProxyMessage.StaffNotice notice -> {
                    out.writeByte(STAFF_NOTICE);
                    out.writeUTF(notice.permission());
                    out.writeUTF(notice.noticeJson());
                }
                case ProxyMessage.Setting setting -> {
                    out.writeByte(SETTING);
                    out.writeUTF(setting.setting().key());
                    out.writeUTF(setting.value());
                }
                case ProxyMessage.PackRequest _ -> out.writeByte(PACK_REQUEST);
                case ProxyMessage.FriendsRequest request -> {
                    out.writeByte(FRIENDS_REQUEST);
                    uuid(out, request.id());
                    out.writeUTF(request.action().name());
                    out.writeUTF(request.name());
                }
                case ProxyMessage.FriendsReply reply -> {
                    out.writeByte(FRIENDS_REPLY);
                    uuid(out, reply.id());
                    out.writeUTF(reply.status().name());
                    out.writeInt(reply.friends().size());
                    for (var friend : reply.friends()) {
                        out.writeUTF(friend.name());
                        out.writeBoolean(friend.online());
                        out.writeUTF(friend.server());
                        out.writeBoolean(friend.watching());
                        if (version == FRIEND_AGES_VERSION) {
                            out.writeLong(friend.days());
                        }
                    }
                    out.writeInt(reply.requests().size());
                    for (var name : reply.requests()) {
                        out.writeUTF(name);
                    }
                }
                case ProxyMessage.Pack pack -> {
                    out.writeByte(PACK);
                    out.writeUTF(pack.status().name());
                }
                case ProxyMessage.Spectate spectate -> {
                    out.writeByte(SPECTATE);
                    uuid(out, spectate.friend());
                }
                case ProxyMessage.Ignore ignore -> {
                    out.writeByte(IGNORE);
                    uuid(out, ignore.other());
                    out.writeBoolean(ignore.ignoring());
                }
                case ProxyMessage.Welcome welcome -> {
                    out.writeByte(WELCOME);
                    out.writeUTF(welcome.rulesJson());
                }
                case ProxyMessage.Announcement announcement -> {
                    out.writeByte(ANNOUNCEMENT);
                    out.writeUTF(announcement.noticeJson());
                }
                case ProxyMessage.Rematch rematch -> {
                    out.writeByte(REMATCH);
                    uuid(out, rematch.opponent());
                }
                case ProxyMessage.Newcomer newcomer -> {
                    out.writeByte(NEWCOMER);
                    uuid(out, newcomer.player());
                    out.writeUTF(newcomer.name());
                    out.writeInt(newcomer.seconds());
                }
            }
        });
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Payload exceeds the plugin message limit");
        }
        return bytes;
    }

    /**
     * Empty when the payload comes from a build with another protocol version, names an unknown type, is malformed,
     * or is not a {@code kind} of message, such as a backend's message arriving on a backend.
     */
    public static <T extends ProxyMessage> Optional<T> decode(byte[] bytes, Class<T> kind) {
        if (bytes.length > MAX_BYTES) {
            return Optional.empty();
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readUnsignedByte();
            if (version != VERSION && version != FRIEND_AGES_VERSION) {
                return Optional.empty();
            }
            var message = read(in, version);
            if (version == FRIEND_AGES_VERSION && !(message instanceof ProxyMessage.FriendsReply)) {
                return Optional.empty();
            }
            if (in.available() != 0) {
                return Optional.empty();
            }
            return Optional.of(message).filter(kind::isInstance).map(kind::cast);
        } catch (IOException | IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static ProxyMessage read(DataInputStream in, int version) throws IOException {
        return switch (in.readUnsignedByte()) {
            case PLAY -> new ProxyMessage.Play(in.readUTF());
            case HUB -> new ProxyMessage.Hub();
            case COMMAND -> new ProxyMessage.Command(in.readUTF());
            case NICK -> new ProxyMessage.NickChange(in.readBoolean()
                    ? Optional.of(new Nick(in.readUTF(), in.readUTF(), in.readUTF()))
                    : Optional.empty());
            case VANISH -> new ProxyMessage.Vanish(in.readBoolean());
            case AFK -> new ProxyMessage.Afk(in.readBoolean());
            case STAFF_NOTICE -> new ProxyMessage.StaffNotice(in.readUTF(), in.readUTF());
            case SETTING -> new ProxyMessage.Setting(
                    PlayerSetting.byKey(in.readUTF()).orElseThrow(() -> new IOException("Unknown setting")), in.readUTF());
            case PACK_REQUEST -> new ProxyMessage.PackRequest();
            case FRIENDS_REQUEST -> friendsRequest(in);
            case FRIENDS_REPLY -> friendsReply(in, version);
            case PACK -> new ProxyMessage.Pack(PackStatus.valueOf(in.readUTF()));
            case SPECTATE -> new ProxyMessage.Spectate(uuid(in));
            case IGNORE -> new ProxyMessage.Ignore(uuid(in), in.readBoolean());
            case WELCOME -> new ProxyMessage.Welcome(in.readUTF());
            case ANNOUNCEMENT -> new ProxyMessage.Announcement(in.readUTF());
            case REMATCH -> new ProxyMessage.Rematch(uuid(in));
            case NEWCOMER -> new ProxyMessage.Newcomer(uuid(in), in.readUTF(), in.readInt());
            default -> throw new IOException("Unknown message type");
        };
    }

    private static ProxyMessage.FriendsRequest friendsRequest(DataInputStream in) throws IOException {
        var request = new ProxyMessage.FriendsRequest(uuid(in), ProxyMessage.FriendsRequest.Action.valueOf(in.readUTF()), in.readUTF());
        boolean valid = request.action() == ProxyMessage.FriendsRequest.Action.LIST
                ? request.name().isEmpty()
                : request.name().matches("[A-Za-z0-9_]{3,16}");
        if (!valid) {
            throw new IOException("Invalid friend name");
        }
        return request;
    }

    private static ProxyMessage.FriendsReply friendsReply(DataInputStream in, int version) throws IOException {
        var id = uuid(in);
        var status = ProxyMessage.FriendsReply.Status.valueOf(in.readUTF());
        var friends = new ArrayList<ProxyMessage.FriendsReply.Entry>();
        for (int count = count(in); count > 0; count--) {
            friends.add(new ProxyMessage.FriendsReply.Entry(in.readUTF(), in.readBoolean(), in.readUTF(), in.readBoolean(),
                    version == FRIEND_AGES_VERSION ? in.readLong() : -1));
        }
        var requests = new ArrayList<String>();
        for (int count = count(in); count > 0; count--) {
            requests.add(in.readUTF());
        }
        return new ProxyMessage.FriendsReply(id, status, List.copyOf(friends), List.copyOf(requests));
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_COUNT) {
            throw new IOException("Invalid count");
        }
        return count;
    }

    private static void uuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID uuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(Writer writer) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                writer.write(out);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
