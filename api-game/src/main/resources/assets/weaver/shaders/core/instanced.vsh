#version 330
#extension GL_ARB_separate_shader_objects : require

// Weaver instanced meshes: one mesh drawn many times, each instance with its own
// model matrix, color, and light, from a per-instance vertex buffer.
// Defines: EMISSIVE (no lightmap or directional shading), ALPHA_CUTOUT (fragment).

#ifndef EMISSIVE
#include <minecraft:light.glsl>
#include <minecraft:sample_lightmap.glsl>
#endif
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec3 Normal;
layout(location = 3) in vec4 InstanceModel0;
layout(location = 4) in vec4 InstanceModel1;
layout(location = 5) in vec4 InstanceModel2;
layout(location = 6) in vec4 InstanceModel3;
layout(location = 7) in vec4 InstanceColor;
layout(location = 8) in vec2 InstanceLight;

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec2 texCoord0;

void main() {
    mat4 model = mat4(InstanceModel0, InstanceModel1, InstanceModel2, InstanceModel3);
    vec4 relative = model * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * relative;
    sphericalVertexDistance = fog_spherical_distance(relative.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(relative.xyz);
#ifdef EMISSIVE
    vertexColor = InstanceColor;
    lightMapColor = vec4(1.0);
#else
    vec3 normal = normalize(mat3(model) * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, InstanceColor);
    lightMapColor = sample_lightmap(Sampler2, ivec2(InstanceLight));
#endif
    texCoord0 = UV0;
}
