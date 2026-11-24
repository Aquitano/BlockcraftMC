#version 330
#extension GL_ARB_separate_shader_objects : require

#include <blockcraft:target_outline.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    fragColor = target_marker(color) == 0 ? color : vec4(0.0);
}
