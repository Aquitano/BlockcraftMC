# syntax=docker/dockerfile:1.7
# The Paper servers' image (README, "The Paper image"). Paper is patched once at build time from the jar stagePlugins
# downloads for the catalog's version, and start-paper only copies the plugins and configs before it starts Java, which
# saves the 14 s the itzg entrypoint spent on every start. Training boots then leave an AOT cache of the classes Paper
# loads while it starts, which takes another 6 s off a game's start.
FROM itzg/minecraft-server:2026.10.0-java25 AS itzg

FROM eclipse-temurin:27-jre AS base

FROM base AS paper
COPY --from=server paper.jar version /tmp/
# A JFR recording adds jdk.jfr to the modules, and the AOT cache is dropped when they differ from the training boots'.
RUN java -DbundlerRepoDir=/opt/paper -Dpaperclip.patchonly=true -jar /tmp/paper.jar \
 && rm -rf /opt/paper/cache \
 && cp /tmp/version /opt/paper/version \
 && { echo --enable-native-access=ALL-UNNAMED; \
      echo --add-modules=jdk.jfr; \
      echo -cp; \
      { find /opt/paper/versions -name '*.jar'; find /opt/paper/libraries -name '*.jar' | sort; } | paste -sd:; \
      echo org.bukkit.craftbukkit.Main; } > /opt/paper/launch.args

FROM base
COPY --from=itzg /usr/local/bin/mc-server-runner /usr/local/bin/rcon-cli /usr/local/bin/mc-monitor /usr/local/bin/
COPY --from=paper /opt/paper /opt/paper
COPY start-paper mc-send-to-console train-aot /usr/local/bin/
# The cache is only used with the jars it was made from, so it is made here, after they reached their final layer.
RUN train-aot && install -d -o 1000 -g 1000 /data
ENV HOME=/data
USER 1000:1000
WORKDIR /data
HEALTHCHECK --start-period=2m --interval=30s --retries=2 CMD ["mc-monitor", "status", "--host", "localhost", "--port", "25565"]
ENTRYPOINT ["start-paper"]
