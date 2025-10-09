precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;

void main() {
    // Sample the color from the texture
    gl_FragColor = texture2D(u_Texture, v_TexCoord);
}