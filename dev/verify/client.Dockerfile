# The real Minecraft client on a virtual display with Mesa's software renderer, built and started by Loom's runClient.
FROM eclipse-temurin:25-jdk
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb xauth libgl1 libglx-mesa0 libgl1-mesa-dri libegl1 libopengl0 libvulkan1 mesa-vulkan-drivers \
    libx11-6 libxcursor1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 libxss1 libxtst6 libxkbcommon0 libxkbcommon-x11-0 \
    libasound2t64 libpulse0 libdbus-1-3 libudev1 libdecor-0-0 libwayland-client0 libwayland-cursor0 libwayland-egl1 \
    libfreetype6 fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && install -d -o 1000 -g 1000 /gradle && install -d -m 1777 /tmp/.X11-unix
