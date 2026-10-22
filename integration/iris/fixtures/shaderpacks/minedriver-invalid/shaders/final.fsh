#version 330 compatibility

in vec2 texcoord;
out vec4 fragColor;

void main() {
    // A valid pack and preprocessor input that fails actual GLSL compilation.
    fragColor = MINEDRIVER_EXPECTED_COMPILE_FAILURE;
}
