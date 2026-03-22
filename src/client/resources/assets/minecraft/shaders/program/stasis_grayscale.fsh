#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D TrailSampler;
uniform float Progress;
uniform float Radius;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    vec4 trail = texture(TrailSampler, texCoord);

    vec2 center = vec2(0.5, 0.5);
    float distanceFromCenter = distance(texCoord, center) / 0.70710678;
    float pixel = 1.0 / max(InSize.x, InSize.y);
    float feather = mix(0.18, 0.03, clamp(Progress, 0.0, 1.0)) + pixel;
    float grayscaleMask = 1.0 - smoothstep(Radius - feather, Radius + feather, distanceFromCenter);
    float desaturateAmount = clamp(grayscaleMask * Progress, 0.0, 1.0);

    float worldWeight = max(1.0 - trail.a, 1.0E-4);
    vec3 worldColor = clamp((color.rgb - trail.rgb) / worldWeight, 0.0, 1.0);
    float worldGrayscale = dot(worldColor, vec3(0.299, 0.587, 0.114));
    vec3 stasisColor = vec3(worldGrayscale) * (1.0 - trail.a) + trail.rgb;
    vec3 result = mix(color.rgb, stasisColor, desaturateAmount);
    fragColor = vec4(result, 1.0);
}
