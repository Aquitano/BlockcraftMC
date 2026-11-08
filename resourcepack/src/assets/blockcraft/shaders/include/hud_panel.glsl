#ifndef BLOCKCRAFT_HUD_PANEL_GLSL
#define BLOCKCRAFT_HUD_PANEL_GLSL

// A boss bar line drawn in colours (231, row, 176 + tint) is the panel in the screen's top-left corner. The server lays
// it out to take no width, so the client starts it in the screen's middle. Each glyph hangs row pixels below the panel's
// top: a glyph placed lower by its font would leave the boss bar line that the shader finds it by.
const int BLOCKCRAFT_PANEL_RED = 231;
const int BLOCKCRAFT_PANEL_FIRST_TINT = 176;
const vec2 BLOCKCRAFT_PANEL_CORNER = vec2(4.0, 4.0);
const vec3 BLOCKCRAFT_PANEL_TINTS[6] = vec3[](
    vec3(1.0),                          // white
    vec3(0.6),                          // dimmed
    vec3(85.0, 255.0, 255.0) / 255.0,   // aqua, the Republic
    vec3(255.0, 85.0, 85.0) / 255.0,    // red, the Separatists
    vec3(255.0, 170.0, 0.0) / 255.0,    // gold, the player's fireteam
    vec3(0.12)                          // shadow
);

int blockcraft_panel_tint(ivec3 marker) {
    int tint = marker.b - BLOCKCRAFT_PANEL_FIRST_TINT;
    return marker.r == BLOCKCRAFT_PANEL_RED && tint >= 0 && tint < 6 ? tint : -1;
}

// Boss bar lines start 3 pixels down and 19 apart, and every panel glyph, shadow included, lies within 17 pixels of
// its line's top, so the line is the one above the vertex. The client centres a line on half its rounded-up GUI width,
// while the projection spans the unrounded one.
vec2 blockcraft_panel_position(vec2 position, int row, mat4 projection) {
    float guiWidth = ceil(2.0 / projection[0][0] - 0.001);
    float lineTop = 3.0 + 19.0 * floor((position.y - 2.0) / 19.0);
    return BLOCKCRAFT_PANEL_CORNER + vec2(position.x - floor(guiWidth / 2.0), float(row) + position.y - lineTop);
}

#endif
