#version 330

uniform sampler2D InSampler;
uniform sampler2D TrailSampler;
uniform sampler2D InDepthSampler;
uniform sampler2D TrailDepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 TrailSize;
    vec2 InDepthSize;
    vec2 TrailDepthSize;
};

layout(std140) uniform StasisConfig {
    float Progress;
    float Radius;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    vec4 trail = texture(TrailSampler, texCoord);
    float sceneDepth = texture(InDepthSampler, texCoord).r;
    float trailDepth = texture(TrailDepthSampler, texCoord).r;

    vec2 center = vec2(0.5, 0.5);
    float distanceFromCenter = distance(texCoord, center) / 0.70710678;
    float pixel = 1.0 / max(InSize.x, InSize.y);
    float feather = mix(0.18, 0.03, clamp(Progress, 0.0, 1.0)) + pixel;
    float grayscaleMask = 1.0 - smoothstep(Radius - feather, Radius + feather, distanceFromCenter);
    float desaturateAmount = clamp(grayscaleMask * Progress, 0.0, 1.0);

    float visibleTrailAlpha = trail.a;
    if (visibleTrailAlpha > 0.0 && trailDepth > sceneDepth + 1.0E-5) {
        visibleTrailAlpha = 0.0;
    }

    float grayscale = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec3 resultWorld = mix(color.rgb, vec3(grayscale), desaturateAmount);
    vec3 result = resultWorld * (1.0 - visibleTrailAlpha) + trail.rgb;
    fragColor = vec4(result, 1.0);
}
