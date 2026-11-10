package me.blockcraft.replay.recording;

import java.util.concurrent.CompletableFuture;

final class PublicationGate<T> {

    private final CompletableFuture<Void> ended = new CompletableFuture<>();
    private final CompletableFuture<T> durable = new CompletableFuture<>();

    CompletableFuture<T> ready() {
        return durable.thenCombine(ended, (recording, ignored) -> recording);
    }

    void completed(T recording) {
        durable.complete(recording);
    }

    void ended() {
        ended.complete(null);
    }
}
