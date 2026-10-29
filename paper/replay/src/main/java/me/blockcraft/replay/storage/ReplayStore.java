package me.blockcraft.replay.storage;

import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayEvent;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public final class ReplayStore implements AutoCloseable {
    public record Saved(ReplayHeader header, long bytes, int durationTicks, String endReason) { }

    private final Path directory;
    private final ReplayLimits limits;
    private final long maxStoredBytes;
    private final long minFreeBytes;
    private volatile Thread writerThread;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = Thread.ofPlatform().daemon().name("blockcraft-replay-writer").unstarted(task);
        writerThread = thread;
        return thread;
    });
    private Writer active;
    private final Set<Writer> pendingWriters = new HashSet<>();
    private boolean closed;

    public ReplayStore(Path directory, ReplayLimits limits) {
        this(directory, limits, 8L * 1024 * 1024 * 1024, 512L * 1024 * 1024);
    }

    public ReplayStore(Path directory, ReplayLimits limits, long maxStoredBytes, long minFreeBytes) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        this.limits = Objects.requireNonNull(limits);
        this.maxStoredBytes = maxStoredBytes;
        this.minFreeBytes = minFreeBytes;
        if (limits.queueCapacity() < 1 || limits.maxEvents() < 1 || limits.maxArchiveBytes() < 1
                || limits.maxDecodedBytes() < 1 || limits.maxDurationTicks() < 1
                || maxStoredBytes < 1 || minFreeBytes < 0) {
            throw new IllegalArgumentException("Replay limits must be positive");
        }
    }

    public synchronized Writer begin(ReplayHeader header) throws IOException {
        if (closed) throw new IOException("Replay store is closed");
        if (active != null) throw new IOException("A replay is already recording or saving");
        if (pendingWriters.size() >= 2) throw new IOException("Replay save backlog is full");
        Writer writer = new Writer(Objects.requireNonNull(header));
        active = writer;
        pendingWriters.add(writer);
        worker.execute(writer::write);
        return writer;
    }

    public Path path(UUID id) {
        return directory.resolve(id + ".bcr");
    }

    public void markReady(UUID id) throws IOException {
        if (!Files.isRegularFile(path(id), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot publish a missing replay archive");
        }
        Path marker = directory.resolve(id + ".ready");
        try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{1}));
            channel.force(true);
        } catch (FileAlreadyExistsException exists) {
            if (!isReady(id)) throw new IOException("Replay publication marker is not a regular file", exists);
        }
    }

    public boolean isReady(UUID id) {
        return Files.isRegularFile(directory.resolve(id + ".ready"), LinkOption.NOFOLLOW_LINKS);
    }

    public CompletableFuture<Void> markReadyAsync(UUID id) {
        var result = new CompletableFuture<Void>();
        Runnable mark = () -> {
            try {
                markReady(id);
                result.complete(null);
            } catch (IOException | RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        };
        if (Thread.currentThread() == writerThread) {
            mark.run();
        } else {
            try {
                worker.execute(mark);
            } catch (RejectedExecutionException failure) {
                result.completeExceptionally(failure);
            }
        }
        return result;
    }

    public ReplayData load(UUID id) throws IOException {
        return read(id, true);
    }

    public Saved inspect(UUID id) throws IOException {
        ReplayData data = read(id, false);
        return new Saved(data.header(), Files.size(path(id)), data.durationTicks(), data.endReason());
    }

    private ReplayData read(UUID id, boolean retainEvents) throws IOException {
        Path file = path(id);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Replay is not a regular file");
        if (Files.size(file) > limits.maxArchiveBytes()) throw new IOException("Replay archive exceeds byte budget");
        try (var raw = new BufferedInputStream(Files.newInputStream(file));
             var compressed = ReplayStreams.limited(raw, limits.maxArchiveBytes());
             var gzip = new ReplayStreams.StrictGzipInput(compressed);
             var in = new DataInputStream(ReplayStreams.limited(gzip, limits.maxDecodedBytes()))) {
            ReplayHeader header = ReplayCodec.readHeader(in, limits);
            if (!header.id().equals(id)) throw new IOException("Replay ID does not match filename");
            return new ReplayCodec(limits, header).read(in, header, retainEvents);
        }
    }

    private void checkDiskBudget() throws IOException {
        long remaining = maxStoredBytes;
        try (var entries = Files.newDirectoryStream(directory, "*.{bcr,part}")) {
            for (Path file : entries) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = file.getFileName().toString();
                String id = name.substring(0, name.lastIndexOf('.'));
                try {
                    if (!UUID.fromString(id).toString().equals(id)) continue;
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                long size = Files.size(file);
                if (size > remaining) throw new IOException("Replay storage budget exhausted");
                remaining -= size;
            }
        }
        if (limits.maxArchiveBytes() > remaining) throw new IOException("Replay storage budget exhausted");
        long usable = Files.getFileStore(directory).getUsableSpace();
        if (usable < minFreeBytes || limits.maxArchiveBytes() > usable - minFreeBytes) {
            throw new IOException("Insufficient free disk space for replay");
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (closed) return;
            closed = true;
            if (active != null) active.finish(limits.maxDurationTicks(), "shutdown");
            worker.shutdown();
        }
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                synchronized (this) {
                    for (Writer writer : List.copyOf(pendingWriters)) {
                        writer.abort();
                        writer.result.completeExceptionally(new IOException("Replay writer shutdown timed out"));
                    }
                }
                worker.shutdownNow();
                throw new IOException("Replay writer did not drain within five seconds");
            }
        } catch (InterruptedException failure) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing replay storage", failure);
        }
    }

    public final class Writer {
        private final ReplayHeader header;
        private final ReplayCodec codec;
        private final ArrayBlockingQueue<Supplier<ReplayEvent>> queue = new ArrayBlockingQueue<>(limits.queueCapacity());
        private final CompletableFuture<Saved> result = new CompletableFuture<>();
        private boolean finishing;
        private boolean aborted;
        private int duration;
        private String reason;

        private Writer(ReplayHeader header) throws IOException {
            this.header = header;
            this.codec = new ReplayCodec(limits, header);
        }

        public synchronized boolean offer(Supplier<ReplayEvent> event) {
            return !finishing && !aborted && !result.isDone() && queue.offer(Objects.requireNonNull(event));
        }

        public CompletableFuture<Saved> finish(int durationTicks, String endReason) {
            synchronized (ReplayStore.this) {
                synchronized (this) {
                    if (!finishing) {
                        finishing = true;
                        duration = durationTicks;
                        reason = Objects.requireNonNull(endReason);
                    }
                    if (active == this) active = null;
                }
            }
            return result;
        }

        public void abort() {
            synchronized (ReplayStore.this) {
                synchronized (this) {
                    aborted = true;
                    queue.clear();
                    if (active == this) active = null;
                }
            }
        }

        private void write() {
            Path temporary = directory.resolve(header.id() + ".part");
            boolean created = false;
            Saved saved = null;
            Throwable failure = null;
            try {
                Files.createDirectories(directory);
                checkDiskBudget();
                if (Files.exists(path(header.id()))) throw new IOException("Replay already exists");
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    created = true;
                    OutputStream compressed = ReplayStreams.limited(Channels.newOutputStream(channel), limits.maxArchiveBytes());
                    try (GZIPOutputStream gzip = new GZIPOutputStream(new BufferedOutputStream(compressed, 65_536))) {
                        DataOutputStream out = new DataOutputStream(ReplayStreams.limited(gzip, limits.maxDecodedBytes()));
                        ReplayCodec.writeHeader(out, header);
                        while (true) {
                            synchronized (this) {
                                if (aborted) throw new IOException("Replay recording aborted");
                                if (finishing && queue.isEmpty()) break;
                            }
                            Supplier<ReplayEvent> event = queue.poll(100, TimeUnit.MILLISECONDS);
                            if (event != null) codec.writeEvent(out, Objects.requireNonNull(event.get()));
                        }
                        codec.writeFooter(out, duration, reason);
                        out.flush();
                        gzip.finish();
                        gzip.flush();
                        channel.force(true);
                    }
                }
                synchronized (this) {
                    if (aborted) throw new IOException("Replay recording aborted");
                    Files.move(temporary, path(header.id()), StandardCopyOption.ATOMIC_MOVE);
                }
                saved = new Saved(header, Files.size(path(header.id())), duration, reason);
            } catch (Exception exception) {
                failure = exception;
                if (created) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            } finally {
                synchronized (this) {
                    finishing = true;
                    queue.clear();
                }
                synchronized (ReplayStore.this) {
                    if (active == this) active = null;
                    pendingWriters.remove(this);
                }
            }
            if (failure == null) result.complete(saved);
            else result.completeExceptionally(failure);
        }
    }
}
