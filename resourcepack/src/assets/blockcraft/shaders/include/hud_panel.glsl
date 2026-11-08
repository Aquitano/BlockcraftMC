#ifndef BLOCKCRAFT_HUD_PANEL_GLSL
#define BLOCKCRAFT_HUD_PANEL_GLSL

// GUI text drawn in colours (red, row, 176 + tint) is a panel in a corner of the screen: red 231 on a boss bar line
// goes to the top-left corner, red 233 on a boss bar line to the top-right, and red 232 on the action bar to the
// bottom-right. The server starts the panel in the screen's middle and lays it out to take no width; a right-hand
// panel's glyphs lie left of its start. Each glyph hangs row pixels below the panel's top: a glyph placed lower by its
// font would leave the line that the shader finds it by.
const int BLOCKCRAFT_PANEL_RED = 231;
const int BLOCKCRAFT_ACTION_PANEL_RED = 232;
const int BLOCKCRAFT_RIGHT_PANEL_RED = 233;
const int BLOCKCRAFT_PANEL_FIRST_TINT = 176;
const vec2 BLOCKCRAFT_PANEL_CORNER = vec2(4.0, 4.0);
// The top-right panel's top-right corner, from the top-right corner of the screen.
const vec2 BLOCKCRAFT_TOP_RIGHT_PANEL_CORNER = vec2(-4.0, 4.0);
// The action bar panel's top-right corner, from the bottom-right corner of the screen.
const vec2 BLOCKCRAFT_ACTION_PANEL_CORNER = vec2(-3.0, -28.0);
const int BLOCKCRAFT_PANEL_TINT_COUNT = 7;
const vec3 BLOCKCRAFT_PANEL_TINTS[BLOCKCRAFT_PANEL_TINT_COUNT] = vec3[](
    vec3(1.0),                          // white
    vec3(0.6),                          // dimmed
    vec3(85.0, 255.0, 255.0) / 255.0,   // aqua, the Republic
    vec3(255.0, 85.0, 85.0) / 255.0,    // red, the Separatists
    vec3(255.0, 170.0, 0.0) / 255.0,    // gold, the player's fireteam
    vec3(0.12),                         // shadow
    vec3(255.0, 255.0, 85.0) / 255.0    // yellow, where to go
);

int blockcraft_panel_tint(ivec3 marker) {
    int tint = marker.b - BLOCKCRAFT_PANEL_FIRST_TINT;
    bool panel = marker.r == BLOCKCRAFT_PANEL_RED || marker.r == BLOCKCRAFT_ACTION_PANEL_RED || marker.r == BLOCKCRAFT_RIGHT_PANEL_RED;
    return panel && tint >= 0 && tint < BLOCKCRAFT_PANEL_TINT_COUNT ? tint : -1;
}

// The client centres a line on half its rounded-up GUI size, while the projection spans the unrounded one. Boss bar
// lines start 3 pixels down and 19 apart, and every panel glyph, shadow included, lies within 17 pixels of its line's
// top, so the line is the one above the vertex. The action bar's line starts 72 pixels above the bottom.
vec2 blockcraft_panel_position(vec2 position, ivec3 marker, mat4 projection) {
    vec2 gui = ceil(2.0 / abs(vec2(projection[0][0], projection[1][1])) - 0.001);
    float fromMiddle = position.x - floor(gui.x / 2.0);
    if (marker.r == BLOCKCRAFT_ACTION_PANEL_RED) {
        float lineTop = gui.y - 72.0;
        return gui + BLOCKCRAFT_ACTION_PANEL_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
    }
    float lineTop = 3.0 + 19.0 * floor((position.y - 2.0) / 19.0);
    if (marker.r == BLOCKCRAFT_RIGHT_PANEL_RED) {
        return vec2(gui.x, 0.0) + BLOCKCRAFT_TOP_RIGHT_PANEL_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
    }
    return BLOCKCRAFT_PANEL_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
}

#endif
