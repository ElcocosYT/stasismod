#version 150

in vec3 Position;
in vec2 UV;

out vec2 texCoord;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

void main() {
	gl_Position = ProjMat * vec4(Position, 1.0);
	texCoord = UV;
}
