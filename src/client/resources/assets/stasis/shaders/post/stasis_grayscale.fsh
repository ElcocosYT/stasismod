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
    float aspect = InSize.x / InSize.y;
    vec2 diff = texCoord - center;
    diff.x *= aspect;
    float maxDist = length(vec2(0.5 * aspect, 0.5));
    float distanceFromCenter = length(diff) / maxDist;
    float pixel = 1.0 / max(InSize.x, InSize.y);
    float feather = mix(0.18, 0.03, clamp(Progress, 0.0, 1.0)) + pixel;
    
    float grayscaleMask = 1.0 - smoothstep(Radius - feather, Radius + feather, distanceFromCenter);
    float desaturateAmount = clamp(grayscaleMask * Progress, 0.0, 1.0);

    // Chromatic aberration at the advancing edge of the grayscale
    float edgeMask = 4.0 * grayscaleMask * (1.0 - grayscaleMask); // peak at edge
    vec2 uvDir = length(texCoord - center) > 0.001 ? normalize(texCoord - center) : vec2(0.0);
    // Delay aberration onset smoothly after start
    float delayedProgress = smoothstep(0.2, 0.5, Progress);
    float aberrationStrength = 0.015 * edgeMask * delayedProgress;
    
    float r = texture(InSampler, texCoord + uvDir * aberrationStrength).r;
    float b = texture(InSampler, texCoord - uvDir * aberrationStrength).b;
    vec3 chromaticColor = vec3(r, color.g, b);
    
    // The base scene color with chromatic aberration applied nicely at the edge
    vec3 baseColor = mix(color.rgb, chromaticColor, edgeMask);

    float worldWeight = max(1.0 - trail.a, 1.0E-4);
    vec3 worldColor = clamp((baseColor - trail.rgb) / worldWeight, 0.0, 1.0);
    float worldGrayscale = dot(worldColor, vec3(0.299, 0.587, 0.114));
    vec3 stasisColor = vec3(worldGrayscale) * (1.0 - trail.a) + trail.rgb;
    
    vec3 result = mix(baseColor, stasisColor, desaturateAmount);

    fragColor = vec4(result, 1.0);
}
