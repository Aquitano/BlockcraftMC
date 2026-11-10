#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <blockcraft:target_outline.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a == 0.0) discard;
    vec3 color = ColorModulator.rgb * vertexColor.rgb;
    ivec3 bytes = ivec3(round(color * 255.0));
    int marker = all(equal(bytes, ivec3(0, 170, 170))) ? 1 : all(equal(bytes, ivec3(170, 0, 0))) ? 2 : 0;
    if (marker != 0 && !gl_FrontFacing) discard;
    fragColor = marker == 0 ? vec4(color, ColorModulator.a) : vec4(target_pack_depth(gl_FragCoord.z), float(marker) / 255.0);
}
