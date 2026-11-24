#version 330
#extension GL_ARB_separate_shader_objects : require

#include <blockcraft:target_outline.glsl>

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

bool visible(float targetDepth, float sceneDepth) {
    return targetDepth >= sceneDepth * 0.999996;
}

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 position = clamp(ivec2(texCoord * size), ivec2(0), size - 1);
    int center = target_marker(texelFetch(InSampler, position, 0));
    float destinationDepth = texelFetch(DepthSampler, position, 0).r;
    bool edge = false;
    float closest = -1.0;
    int selected = 0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            ivec2 source = clamp(position + ivec2(x, y), ivec2(0), size - 1);
            vec4 sampleColor = texelFetch(InSampler, source, 0);
            int marker = target_marker(sampleColor);
            edge = edge || (center == 0) != (marker == 0);
            if (marker == 0) continue;
            float depth = target_unpack_depth(sampleColor.rgb);
            if (depth > closest && visible(depth, destinationDepth) && visible(depth, texelFetch(DepthSampler, source, 0).r)) {
                closest = depth;
                selected = marker;
            }
        }
    }
    fragColor = edge && selected != 0 ? vec4(target_color(selected), 1.0) : vec4(0.0);
}
