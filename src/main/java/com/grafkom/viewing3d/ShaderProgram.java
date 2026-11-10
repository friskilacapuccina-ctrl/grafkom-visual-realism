package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL20.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class ShaderProgram {
    private final int programId;
    private final Map<String, Integer> cache = new HashMap<>();

    public ShaderProgram(String vertexPath, String fragmentPath) {
        int vs = compile(GL_VERTEX_SHADER, readResource(vertexPath), vertexPath);
        int fs = compile(GL_FRAGMENT_SHADER, readResource(fragmentPath), fragmentPath);
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Link shader gagal:\n" + glGetProgramInfoLog(programId));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src, String name) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader gagal dikompilasi (" + name + "):\n" + glGetShaderInfoLog(id));
        }
        return id;
    }

    private static String readResource(String path) {
        try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new RuntimeException("Resource tidak ditemukan: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void use() { glUseProgram(programId); }

    private int loc(String name) {
        return cache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setInt(String n, int v) { glUniform1i(loc(n), v); }
    public void setBool(String n, boolean v) { glUniform1i(loc(n), v ? 1 : 0); }
    public void setFloat(String n, float v) { glUniform1f(loc(n), v); }
    public void setVec3(String n, Vector3f v) { glUniform3f(loc(n), v.x, v.y, v.z); }
    public void setMat4(String n, Matrix4f m) { glUniformMatrix4fv(loc(n), false, m.get(new float[16])); }
}