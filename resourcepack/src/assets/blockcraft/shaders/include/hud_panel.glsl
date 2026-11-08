#ifndef BLOCKCRAFT_HUD_PANEL_GLSL
#define BLOCKCRAFT_HUD_PANEL_GLSL

// Every GUI text colour the text shader reads as an instruction rather than a colour. Ordinary text must avoid them and
// the quarter-bright shadows the client derives from them.
//
//   red  green  blue        meaning                                                      written by
//   231  row    176 + tint  a panel in the top-left corner, on a boss bar line           HudPanel.Corner.TOP_LEFT
//   233  row    176 + tint  a panel in the top-right corner, on a boss bar line          HudPanel.Corner.TOP_RIGHT
//   232  row    176 + tint  a panel in the bottom-right corner, on the action bar        HudPanel.Corner.BOTTOM_RIGHT
//   231  1-5    197         a text effect from text_effect.glsl                          TextMotion
//
// Blue 176 to 182 is a panel tint, an index into BLOCKCRAFT_PANEL_TINTS in the order of HudPanel.Tint. Blue 197 is a
// text effect and never a tint.
//
// The server starts a panel in the screen's middle and lays it out to take no width; a right-hand panel's glyphs lie
// left of its start. Each glyph hangs row pixels below the panel's top: a glyph placed lower by its font would leave
// the line that the shader finds it by.
const int BLOCKCRAFT_TOP_LEFT_RED = 231;
const int BLOCKCRAFT_BOTTOM_RIGHT_RED = 232;
const int BLOCKCRAFT_TOP_RIGHT_RED = 233;
const int BLOCKCRAFT_PANEL_FIRST_TINT = 176;
const int BLOCKCRAFT_PANEL_TINT_COUNT = 7;
const vec3 BLOCKCRAFT_PANEL_TINTS[BLOCKCRAFT_PANEL_TINT_COUNT] = vec3[](
    vec3(1.0),                          // WHITE
    vec3(0.6),                          // DIMMED
    vec3(85.0, 255.0, 255.0) / 255.0,   // REPUBLIC, aqua
    vec3(255.0, 85.0, 85.0) / 255.0,    // SEPARATISTS, red
    vec3(255.0, 170.0, 0.0) / 255.0,    // SQUAD, gold: the player's fireteam
    vec3(0.12),                         // SHADOW
    vec3(255.0, 255.0, 85.0) / 255.0    // ACCENT, yellow: where to go
);

// Each panel's corner, from the screen's corner of the same name. The bottom-right panel hangs from the top of a full
// column of ability tiles, AbilityPanel.HEIGHT pixels tall.
const vec2 BLOCKCRAFT_TOP_LEFT_CORNER = vec2(4.0, 4.0);
const vec2 BLOCKCRAFT_TOP_RIGHT_CORNER = vec2(-4.0, 4.0);
const float BLOCKCRAFT_ABILITY_COLUMN = 108.0;
const float BLOCKCRAFT_BOTTOM_RIGHT_MARGIN = 3.0;
const vec2 BLOCKCRAFT_BOTTOM_RIGHT_CORNER = vec2(-BLOCKCRAFT_BOTTOM_RIGHT_MARGIN,
        -BLOCKCRAFT_BOTTOM_RIGHT_MARGIN - BLOCKCRAFT_ABILITY_COLUMN);

// Where the client draws the lines that carry panels: boss bar text lines from 3 pixels below the screen's top and 19
// apart, and the action bar 72 pixels above its bottom. A panel's vertices, shadows included, lie from one pixel above
// their line's top to 17 below it, so a vertex came from the last line that starts no more than a pixel below it.
const float BLOCKCRAFT_BOSS_BAR_TOP = 3.0;
const float BLOCKCRAFT_BOSS_BAR_PITCH = 19.0;
const float BLOCKCRAFT_ABOVE_LINE = 1.0;
const float BLOCKCRAFT_ACTION_BAR_TOP = 72.0;

int blockcraft_panel_tint(ivec3 marker) {
    int tint = marker.b - BLOCKCRAFT_PANEL_FIRST_TINT;
    bool panel = marker.r == BLOCKCRAFT_TOP_LEFT_RED || marker.r == BLOCKCRAFT_BOTTOM_RIGHT_RED || marker.r == BLOCKCRAFT_TOP_RIGHT_RED;
    return panel && tint >= 0 && tint < BLOCKCRAFT_PANEL_TINT_COUNT ? tint : -1;
}

// The client centres a line on half its rounded-up GUI size, while the projection spans the unrounded one.
vec2 blockcraft_panel_position(vec2 position, ivec3 marker, mat4 projection) {
    vec2 gui = ceil(2.0 / abs(vec2(projection[0][0], projection[1][1])) - 0.001);
    float fromMiddle = position.x - floor(gui.x / 2.0);
    if (marker.r == BLOCKCRAFT_BOTTOM_RIGHT_RED) {
        float lineTop = gui.y - BLOCKCRAFT_ACTION_BAR_TOP;
        return gui + BLOCKCRAFT_BOTTOM_RIGHT_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
    }
    float lineTop = BLOCKCRAFT_BOSS_BAR_TOP + BLOCKCRAFT_BOSS_BAR_PITCH
            * floor((position.y - BLOCKCRAFT_BOSS_BAR_TOP + BLOCKCRAFT_ABOVE_LINE) / BLOCKCRAFT_BOSS_BAR_PITCH);
    if (marker.r == BLOCKCRAFT_TOP_RIGHT_RED) {
        return vec2(gui.x, 0.0) + BLOCKCRAFT_TOP_RIGHT_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
    }
    return BLOCKCRAFT_TOP_LEFT_CORNER + vec2(fromMiddle, float(marker.g) + position.y - lineTop);
}

#endif
