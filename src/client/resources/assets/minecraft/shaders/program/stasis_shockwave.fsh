#version 150

uniform sampler2D DiffuseSampler;
uniform float ShockwaveProgress;
uniform vec2 InSize;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 center = vec2(0.5, 0.5);
    float aspect = InSize.x / InSize.y;

    // Aspect-corrected coords
    vec2 delta = uv - center;
    delta.x *= aspect;
    float dist = length(delta);

    // UV-space direction
    vec2 uvDelta = uv - center;
    vec2 uvDir = length(uvDelta) > 0.001 ? normalize(uvDelta) : vec2(0.0);

    // === Primary shockwave ring ===
    float maxRadius = 3.0; // Grows very large to fully exit screen natively
    // Ease-in so it starts extremely tight on the pure center pixel, then accelerates outward
    float ringPos = pow(ShockwaveProgress, 1.3) * maxRadius;
    
    // Thickness starts at 0, quickly grows to 0.15, then thins out cleanly
    float thickness = mix(0.15, 0.02, ShockwaveProgress) * smoothstep(0.0, 0.05, ShockwaveProgress);

    // Smooth ring mask
    float ringDelta = dist - ringPos;
    float ringMask = smoothstep(thickness, 0.0, abs(ringDelta));

    // Fade linearly over the second half
    float fade = 1.0 - smoothstep(0.5, 1.0, ShockwaveProgress);
    ringMask *= fade;

    // === Secondary trailing ripple ===
    float trailPos = max(ringPos - 0.12, 0.0);
    float trailThickness = thickness * 0.8;
    float trailMask = smoothstep(trailThickness, 0.0, abs(dist - trailPos));
    trailMask *= fade * 0.3;

    // === Refraction distortion ===
    // Restored to heavy distortion (0.08) per user request
    float refractSign = ringDelta > 0.0 ? 1.0 : -1.0;
    float distortAmount = 0.08 * ringMask + 0.025 * trailMask;
    vec2 distortedUV = uv + uvDir * distortAmount * refractSign;

    // === Chromatic aberration ===
    float chromatic = 0.022 * ringMask + 0.008 * trailMask;
    float r = texture(DiffuseSampler, distortedUV + uvDir * chromatic).r;
    float g = texture(DiffuseSampler, distortedUV).g;
    float b = texture(DiffuseSampler, distortedUV - uvDir * chromatic).b;
    vec3 color = vec3(r, g, b);

    // Edge highlight (pure light, no color tint)
    float edgeHighlight = pow(ringMask, 1.5) * 0.05;
    color += vec3(edgeHighlight);

    // Tiny center flash (radius 0.02)
    float flashIntensity = max(1.0 - ShockwaveProgress * 20.0, 0.0);
    float flash = smoothstep(0.02, 0.0, dist) * flashIntensity * 0.15;
    color += vec3(flash);

    fragColor = vec4(color, 1.0);
}
