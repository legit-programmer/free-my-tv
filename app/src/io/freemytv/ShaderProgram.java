package io.freemytv;

import android.annotation.TargetApi;
import android.graphics.Shader;
import android.os.Build;

/** The API 28 renderer only sees Shader; AGSL classes are loaded on Android 13+ alone. */
abstract class ShaderProgram {
    static ShaderProgram create(String source) {
        if (Build.VERSION.SDK_INT < 33 || Performance.lightweight) return null;
        return new Api33(source);
    }

    abstract Shader shader();
    abstract void setFloatUniform(String name, float... values);
    abstract void setInputShader(String name, Shader shader);

    @TargetApi(33)
    private static final class Api33 extends ShaderProgram {
        private final android.graphics.RuntimeShader program;
        Api33(String source) { program = new android.graphics.RuntimeShader(source); }
        @Override Shader shader() { return program; }
        @Override void setFloatUniform(String name, float... values) { program.setFloatUniform(name, values); }
        @Override void setInputShader(String name, Shader input) { program.setInputShader(name, input); }
    }
}
