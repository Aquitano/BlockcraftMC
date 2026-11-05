#version 330
#extension GL_ARB_separate_shader_objects : require

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:globals.glsl>
#include <blockcraft:text_effect.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 3) in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) flat out int textEffect;
layout(location = 5) out float textHorizontal;

void main() {
    ivec3 marker = ivec3(round(Color.rgb * 255.0));
    textEffect = marker.r == 231 && marker.b == 197 && marker.g >= 1 && marker.g <= 5 ? marker.g : 0;
    // Shadow tint loses the marker's low green bits; omit that shadow rather than animate the wrong effect.
    bool markerShadow = marker.r >= 57 && marker.r <= 58 && marker.b == 49 && marker.g <= 1;
    vec4 textColor = textEffect != 0 ? vec4(1.0, 1.0, 1.0, Color.a) : Color;
    if (markerShadow) textColor.a = 0.0;
    vec3 position = Position + vec3(blockcraft_text_offset(textEffect, GameTime * 1200.0), 0.0);
    textHorizontal = Position.x;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = textColor * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = textColor;
#endif
    texCoord0 = UV0;
}
