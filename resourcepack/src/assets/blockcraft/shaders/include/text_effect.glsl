#ifndef BLOCKCRAFT_TEXT_EFFECT_GLSL
#define BLOCKCRAFT_TEXT_EFFECT_GLSL

vec2 blockcraft_text_offset(int effect, float seconds) {
    if (effect == 1) return vec2(0.0, sin(seconds * 3.0) * 1.5);
    if (effect == 2) return vec2(sin(seconds * 51.0), cos(seconds * 43.0)) * 0.7;
    return vec2(0.0);
}

vec3 blockcraft_text_color(int effect, float horizontal, float seconds) {
    if (effect == 3) return vec3(0.72 + 0.28 * sin(seconds * 4.0));
    if (effect == 4) {
        return 0.65 + 0.35 * cos(vec3(0.0, 2.094395, 4.188790) + horizontal * 0.035 + seconds * 2.0);
    }
    if (effect == 5) {
        float shine = pow(max(0.0, sin(horizontal * 0.08 - seconds * 4.0)), 16.0);
        return mix(vec3(0.6, 0.78, 0.92), vec3(1.0), shine);
    }
    return vec3(1.0);
}

#endif
