#ifndef BLOCKCRAFT_MATERIAL_EFFECT_GLSL
#define BLOCKCRAFT_MATERIAL_EFFECT_GLSL

vec3 blockcraft_material(int effect, vec3 position, float seconds) {
    if (effect == 1) {
        float scanline = 0.78 + 0.22 * sin(position.y * 60.0 - seconds * 9.0);
        float flicker = 0.94 + 0.06 * sin(seconds * 17.0 + position.x * 13.0);
        return vec3(0.15, 0.85, 1.0) * scanline * flicker;
    }
    if (effect == 2) {
        float wave = sin(position.x * 14.0 + sin(position.y * 10.0 + seconds * 2.0));
        wave += cos(position.z * 12.0 - seconds * 3.0);
        return mix(vec3(0.35, 0.12, 0.9), vec3(1.0, 0.5, 0.95), wave * 0.25 + 0.5);
    }
    float pulse = 0.65 + 0.35 * sin(seconds * 3.0);
    return mix(vec3(0.7, 0.3, 0.04), vec3(1.0, 0.9, 0.4), pulse);
}

#endif
