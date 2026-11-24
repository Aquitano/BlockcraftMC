#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(std140) uniform PresentationConfig {
    vec4 Parameters;
};
layout(location = 0) out vec4 fragColor;

// 0 to 15 over a 4-by-4 tile, so a share of a cell's pixels darken in a fixed pattern rather than all of them a little.
float bayer(vec2 cell) {
    ivec2 at = ivec2(mod(cell, 4.0));
    int index = at.x + at.y * 4;
    const int order[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return float(order[index]);
}

void main() {
    float seconds = GameTime * 1200.0;
    int mode = int(Parameters.z + 0.5);
    vec2 centered = texCoord * 2.0 - 1.0;
    float edge = smoothstep(0.3, 1.35, length(centered));
    vec2 uv = texCoord;
    if (mode == 1) uv.x += sin(texCoord.y * 120.0 + seconds * 3.0) * 0.0008;
    if (mode == 7) {
        float frame = floor(seconds * 30.0);
        vec2 jolt = vec2(fract(sin(frame * 12.9898) * 43758.5453), fract(sin(frame * 78.233) * 43758.5453)) - 0.5;
        uv += floor(jolt * 5.0) * max(1.0, floor(ScreenSize.y / 270.0)) / ScreenSize;
    }
    if (mode == 4) {
        float twist = sin(seconds * 0.8 + length(centered) * 5.0) * edge * 0.018;
        uv += vec2(-centered.y, centered.x) * twist;
    }
    vec4 color = texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999)));
    float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    if (mode == 1) {
        color.rgb = mix(color.rgb, luminance * vec3(0.3, 1.2, 1.45), 0.6);
        color.rgb *= 0.94 + 0.06 * sin(texCoord.y * ScreenSize.y * 1.5 - seconds * 3.0);
    } else if (mode == 2) {
        color.rgb = mix(color.rgb, vec3(luminance) * vec3(0.6, 0.8, 1.05), 0.45);
        color.rgb *= 1.0 - edge * (0.25 + 0.025 * sin(seconds * 0.7));
    } else if (mode == 3) {
        color.rgb = mix(color.rgb, vec3(luminance), edge * 0.75);
        color.rgb *= 1.0 - edge * 0.4;
    } else if (mode == 4) {
        color.rgb = mix(color.rgb, color.rgb * vec3(1.25, 0.65, 1.45), edge * 0.6);
    } else if (mode == 5) {
        color.rgb = mix(color.rgb, vec3(0.55, 0.02, 0.02), smoothstep(0.55, 1.3, length(centered)) * 0.85);
    } else if (mode == 6) {
        float block = max(2.0, floor(ScreenSize.y / 135.0));
        vec2 cell = floor(texCoord * ScreenSize / block);
        float reach = length(((cell + 0.5) * block / ScreenSize) * 2.0 - 1.0);
        float inner = mix(1.12, 0.66, clamp(Parameters.w, 0.0, 1.0));
        float depth = clamp((reach - inner) / 0.45, 0.0, 1.0);
        float shade = depth * 16.0 > bayer(cell) + 0.5 ? 1.0 : 0.0;
        vec3 ash = vec3(luminance) * vec3(0.32, 0.34, 0.38);
        color.rgb = mix(color.rgb, ash, shade * (0.55 + 0.3 * step(0.6, depth)));
    } else if (mode == 7) {
        vec2 split = vec2(max(1.0, floor(ScreenSize.y / 270.0)) * 2.0, 0.0) / ScreenSize;
        color.r = texture(InSampler, clamp(uv + split, vec2(0.001), vec2(0.999))).r;
        color.b = texture(InSampler, clamp(uv - split, vec2(0.001), vec2(0.999))).b;
    }
    if (mode != 0 && mode != 6) color = mix(texture(InSampler, texCoord), color, clamp(Parameters.w, 0.0, 1.0));
    float bars = Parameters.y * (1.0 - step(0.115, texCoord.y) * step(texCoord.y, 0.885));
    color.rgb *= 1.0 - max(clamp(Parameters.x, 0.0, 1.0), bars);
    fragColor = color;
}
