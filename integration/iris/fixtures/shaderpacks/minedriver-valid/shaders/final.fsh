#version 330 compatibility

uniform sampler2D colortex0;
in vec2 texcoord;
out vec4 fragColor;

void main() {
    // Preserve the world on the left; a two-color marker proves this final pass ran.
    if (texcoord.x < 0.5) {
        fragColor = texture(colortex0, texcoord);
    } else {
        fragColor = texcoord.y > 0.5
            ? vec4(32.0, 192.0, 96.0, 255.0) / 255.0
            : vec4(224.0, 32.0, 192.0, 255.0) / 255.0;
    }
}
