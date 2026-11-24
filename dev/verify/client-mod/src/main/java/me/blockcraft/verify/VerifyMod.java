package me.blockcraft.verify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/** Takes one JSON command per line on a local socket and runs it on the client thread; see dev/verify/README.md. */
public final class VerifyMod implements ClientModInitializer {

    private static final int PORT = 25599;
    private static final int COMMAND_SECONDS = 120;
    private static final Gson GSON = new Gson();

    private record Request(JsonObject body, CompletableFuture<JsonObject> reply) { }

    private final Queue<Request> requests = new ConcurrentLinkedQueue<>();
    private final Commands commands = new Commands();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        var thread = new Thread(this::listen, "verify-control");
        thread.setDaemon(true);
        thread.start();
    }

    private void tick(Minecraft minecraft) {
        commands.tick(minecraft);
        for (var request = requests.poll(); request != null; request = requests.poll()) {
            var reply = request.reply();
            if (reply.isDone()) {
                continue;
            }
            try {
                commands.run(minecraft, request.body()).whenComplete((value, failure) -> {
                    if (failure == null) {
                        reply.complete(value);
                    } else {
                        reply.completeExceptionally(failure);
                    }
                });
            } catch (RuntimeException failure) {
                reply.completeExceptionally(failure);
            }
        }
    }

    private void listen() {
        try (var server = new ServerSocket(PORT)) {
            while (!server.isClosed()) {
                var socket = server.accept();
                var thread = new Thread(() -> serve(socket), "verify-session");
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not open the verify port", failure);
        }
    }

    private void serve(Socket socket) {
        try (socket; var in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            for (var line = in.readLine(); line != null; line = in.readLine()) {
                out.println(GSON.toJson(answer(line)));
            }
        } catch (IOException ignored) {
            // The caller hung up; the next caller opens a new connection.
        }
    }

    private JsonObject answer(String line) {
        try {
            var request = new Request(GSON.fromJson(line, JsonObject.class), new CompletableFuture<>());
            requests.add(request);
            var value = request.reply().orTimeout(COMMAND_SECONDS, TimeUnit.SECONDS).join();
            value.addProperty("ok", true);
            return value;
        } catch (RuntimeException failure) {
            var value = new JsonObject();
            value.addProperty("ok", false);
            value.addProperty("error", String.valueOf(failure.getCause() == null ? failure : failure.getCause()));
            return value;
        }
    }
}
