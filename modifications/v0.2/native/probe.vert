#version 450
vec2 vertices[3] = vec2[](vec2(-0.8, -0.8), vec2(0.8, -0.8), vec2(0.0, 0.8));
void main() { gl_Position = vec4(vertices[gl_VertexIndex], 0.0, 1.0); }
