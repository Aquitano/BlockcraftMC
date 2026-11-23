import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

class Hotspots {
    static void main(String[] args) throws IOException {
        if (args.length != 2 || Integer.parseInt(args[1]) < 1) {
            System.err.println("Usage: java Hotspots.java <recordings directory> <top N>");
            System.exit(2);
        }
        int limit = Integer.parseInt(args[1]);
        int recordings = 0;
        try (var services = Files.list(Path.of(args[0]))) {
            for (Path service : services.filter(Files::isDirectory).sorted().toList()) {
                try (var entries = Files.list(service)) {
                    var files = entries.filter(Files::isRegularFile)
                            .filter(file -> file.toString().endsWith(".jfr")).sorted().toList();
                    if (files.isEmpty()) continue;
                    report(service.getFileName().toString(), files, limit);
                    recordings += files.size();
                }
            }
        }
        if (recordings == 0) throw new IOException("No .jfr files in service directories under " + args[0]);
    }

    private static void report(String service, List<Path> files, int limit) throws IOException {
        Map<String, Long> cpu = new HashMap<>();
        Map<String, Long> cpuCallers = new HashMap<>();
        Map<String, Long> allocations = new HashMap<>();
        Map<String, Long> allocationCallers = new HashMap<>();
        var byDuration = Comparator.comparing((Spike spike) -> spike.duration);
        var spikes = new PriorityQueue<>(byDuration);
        Instant first = Instant.MAX;
        Instant last = Instant.MIN;
        int slowTicks = 0;
        long cpuReadings = 0;
        double cpuSum = 0;
        double peakCpu = 0;
        long peakAfterGc = -1;
        long peakRss = -1;
        for (Path file : files) {
            try (var recording = new RecordingFile(file)) {
                while (recording.hasMoreEvents()) {
                    var event = recording.readEvent();
                    var start = event.getStartTime();
                    var end = event.getEndTime();
                    if (start.isBefore(first)) first = start;
                    if (end.isAfter(last)) last = end;
                    switch (event.getEventType().getName()) {
                        case "jdk.ExecutionSample" -> attribute(cpu, cpuCallers, event, 1);
                        case "jdk.ObjectAllocationSample" -> attribute(allocations, allocationCallers, event, event.getLong("weight"));
                        case "jdk.CPULoad" -> {
                            double load = event.getFloat("jvmUser") + event.getFloat("jvmSystem");
                            cpuSum += load;
                            cpuReadings++;
                            peakCpu = Math.max(peakCpu, load);
                        }
                        case "jdk.GCHeapSummary" -> {
                            if ("After GC".equals(event.getString("when"))) {
                                peakAfterGc = Math.max(peakAfterGc, event.getLong("heapUsed"));
                            }
                        }
                        case "jdk.ResidentSetSize" -> peakRss = Math.max(peakRss, event.getLong("peak"));
                        case "blockcraft.SlowTick" -> {
                            slowTicks++;
                            var duration = event.getDuration("tickTime");
                            if (spikes.size() == limit && duration.compareTo(spikes.element().duration) <= 0) continue;
                            spikes.add(new Spike(file, event.getInt("tick"), end, duration, event.getThread().getJavaThreadId()));
                            if (spikes.size() > limit) spikes.remove();
                        }
                    }
                }
            } catch (IOException error) {
                throw new IOException("Could not read " + file, error);
            }
        }
        System.out.printf("%n%s (%d recordings, %d ticks over 50 ms)%n", service, files.size(), slowTicks);
        if (first != Instant.MAX) System.out.printf("Events span %s to %s UTC, with possible gaps.%n", first, last);
        if (cpuReadings > 0) {
            System.out.printf("JVM CPU mean %.2f%%, peak %.2f%%%n", 100 * cpuSum / cpuReadings, 100 * peakCpu);
        }
        if (peakAfterGc >= 0) System.out.printf("Peak heap used after GC %.1f MiB%n", peakAfterGc / 1048576.0);
        if (peakRss >= 0) System.out.printf("Peak JVM RSS %.1f MiB%n", peakRss / 1048576.0);
        printRank("Java CPU methods (sample counts)", cpu, limit);
        printRank("Plugin CPU frames (sample counts)", cpuCallers, limit);
        printRank("Allocation sites (estimated bytes)", allocations, limit);
        printRank("Plugin allocation frames (estimated bytes)", allocationCallers, limit);
        printSpikes(spikes.stream().sorted(byDuration.reversed()).toList());
    }

    private static void attribute(Map<String, Long> methods, Map<String, Long> callers, RecordedEvent event, long weight) {
        var frames = frames(event);
        methods.merge(leaf(frames), weight, Long::sum);
        pluginFrame(frames).ifPresent(name -> callers.merge(name, weight, Long::sum));
    }

    private static List<RecordedFrame> frames(RecordedEvent event) {
        var stack = event.getStackTrace();
        return stack == null ? List.of() : stack.getFrames();
    }

    private static String leaf(List<RecordedFrame> frames) {
        return frames.isEmpty() ? "[no stack]" : method(frames.getFirst());
    }

    private static Optional<String> pluginFrame(List<RecordedFrame> frames) {
        for (var frame : frames) {
            var type = frame.getMethod().getType();
            var loader = type.getClassLoader();
            if (loader != null && loader.getType() != null && loader.getType().getName().endsWith("PluginClassLoader")) {
                var name = loader.getName();
                return Optional.of(name == null ? method(frame) : "[" + name + "] " + method(frame));
            }
            if (type.getName().startsWith("me.blockcraft.")) {
                return Optional.of(method(frame));
            }
        }
        return Optional.empty();
    }

    private static String method(RecordedFrame frame) {
        var method = frame.getMethod();
        return method.getType().getName() + "." + method.getName();
    }

    private static void printRank(String title, Map<String, Long> values, int limit) {
        System.out.println(title);
        values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey())).limit(limit)
                .forEach(entry -> System.out.printf("  %12d  %s%n", entry.getValue(), entry.getKey()));
    }

    private static final class Spike {
        final Path file;
        final int tick;
        final Instant start;
        final Instant end;
        final Duration duration;
        final long thread;
        final Map<String, Long> samples = new HashMap<>();
        double gcMillis;

        Spike(Path file, int tick, Instant end, Duration duration, long thread) {
            this.file = file;
            this.tick = tick;
            this.start = end.minus(duration);
            this.end = end;
            this.duration = duration;
            this.thread = thread;
        }
    }

    private static void printSpikes(List<Spike> spikes) throws IOException {
        if (spikes.isEmpty()) return;
        for (Path file : spikes.stream().map(spike -> spike.file).distinct().toList()) {
            var inFile = spikes.stream().filter(spike -> spike.file.equals(file)).toList();
            try (var recording = new RecordingFile(file)) {
                while (recording.hasMoreEvents()) {
                    var event = recording.readEvent();
                    switch (event.getEventType().getName()) {
                        case "jdk.ExecutionSample" -> {
                            var thread = event.getThread("sampledThread");
                            var time = event.getStartTime();
                            for (var spike : inFile) {
                                if (thread != null && thread.getJavaThreadId() == spike.thread
                                        && !time.isBefore(spike.start) && !time.isAfter(spike.end)) {
                                    var frames = frames(event);
                                    var caller = pluginFrame(frames).orElseGet(() -> leaf(frames));
                                    spike.samples.merge(caller, 1L, Long::sum);
                                }
                            }
                        }
                        case "jdk.GCPhasePause" -> {
                            var pauseStart = event.getStartTime();
                            var pauseEnd = event.getEndTime();
                            for (var spike : inFile) {
                                var start = spike.start.isAfter(pauseStart) ? spike.start : pauseStart;
                                var end = spike.end.isBefore(pauseEnd) ? spike.end : pauseEnd;
                                if (end.isAfter(start)) spike.gcMillis += Duration.between(start, end).toNanos() / 1e6;
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Slowest ticks (UTC, overlapping GC pauses and Java samples on the tick thread)");
        for (var spike : spikes) {
            System.out.printf("  %8.2f ms  %s  tick %d  GC %.2f ms  %s%n", spike.duration.toNanos() / 1e6,
                    spike.end, spike.tick, spike.gcMillis, spike.file.getFileName());
            if (spike.samples.isEmpty()) System.out.println("    No Java samples on the tick thread.");
            else printRank("    Sample counts", spike.samples, 3);
        }
    }
}
