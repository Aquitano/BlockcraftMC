#ifndef BLOCKCRAFT_TARGET_OUTLINE_GLSL
#define BLOCKCRAFT_TARGET_OUTLINE_GLSL

int target_marker(vec4 sampleColor) {
    int marker = int(round(sampleColor.a * 255.0));
    return marker == 1 || marker == 2 ? marker : 0;
}

vec3 target_pack_depth(float depth) {
    uint encoded = uint(round(clamp(-log2(max(depth, exp2(-32.0))) / 32.0, 0.0, 1.0) * 16777215.0));
    return vec3((encoded >> 16u) & 255u, (encoded >> 8u) & 255u, encoded & 255u) / 255.0;
}

float target_unpack_depth(vec3 packedColor) {
    uvec3 bytes = uvec3(round(packedColor * 255.0));
    uint encoded = (bytes.r << 16u) | (bytes.g << 8u) | bytes.b;
    return exp2(-float(encoded) / 16777215.0 * 32.0);
}

vec3 target_color(int marker) {
    return marker == 1 ? vec3(85.0, 255.0, 255.0) / 255.0 : vec3(255.0, 85.0, 85.0) / 255.0;
}

#endif
