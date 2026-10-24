#version 150

// A cheap motion blur for the terminal ride: thirteen taps along the direction of travel (up and down the screen), a little
// fanned out toward the sides, and weaker at the middle so the rider stays sharp. One pass, no depth, no history frames.
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Strength;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 c = texCoord - vec2(0.5);
    float aspect = OutSize.x / OutSize.y;
    float edge = smoothstep(0.08, 0.65, length(c * vec2(aspect, 1.0)));
    float amount = Strength * (0.2 + 0.8 * edge);
    vec2 dir = vec2(c.x * 0.4, 1.0) * amount * 0.10;
    vec4 sum = vec4(0.0);
    float total = 0.0;
    for (int i = -6; i <= 6; i++) {
        float t = float(i) / 6.0;
        float w = 1.0 - abs(t) * 0.65;
        sum += texture(DiffuseSampler, texCoord + dir * t) * w;
        total += w;
    }
    fragColor = vec4((sum / total).rgb, 1.0);
}
