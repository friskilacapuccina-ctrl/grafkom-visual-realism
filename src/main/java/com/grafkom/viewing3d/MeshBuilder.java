package com.grafkom.viewing3d;

import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshBuilder {

    public static class MeshData {
        public float[] flat, smooth;
        public int vertexCount;
    }

    public static MeshData sphere(int stacks, int sectors, float r) {
        int cols = sectors + 1;
        Vector3f[] pos = new Vector3f[(stacks + 1) * cols];
        Vector2f[] uv = new Vector2f[pos.length];
        for (int i = 0; i <= stacks; i++) {
            double th = Math.PI * i / stacks;
            for (int j = 0; j <= sectors; j++) {
                double ph = 2 * Math.PI * j / sectors;
                int k = i * cols + j;
                pos[k] = new Vector3f((float) (Math.sin(th) * Math.cos(ph)),
                        (float) Math.cos(th),
                        (float) (Math.sin(th) * Math.sin(ph))).mul(r);
                uv[k] = new Vector2f((float) j / sectors, 1f - (float) i / stacks);
            }
        }

        List<int[]> tris = new ArrayList<>();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < sectors; j++) {
                int a = i * cols + j, b = a + cols;
                tris.add(new int[]{a, a + 1, b});
                tris.add(new int[]{a + 1, b + 1, b});
            }
        }

        // Normal tiap face, lalu jumlahkan per POSISI (bukan per indeks) agar tidak ada jahitan
        Map<String, Vector3f> sum = new HashMap<>();
        Vector3f[] faceN = new Vector3f[tris.size()];
        for (int t = 0; t < tris.size(); t++) {
            int[] f = tris.get(t);
            Vector3f n = new Vector3f(pos[f[1]]).sub(pos[f[0]])
                    .cross(new Vector3f(pos[f[2]]).sub(pos[f[0]]));
            faceN[t] = n;
            for (int v : f) {
                sum.computeIfAbsent(key(pos[v]), x -> new Vector3f()).add(n);
            }
        }

        MeshData m = new MeshData();
        m.vertexCount = tris.size() * 3;
        m.flat = new float[m.vertexCount * 8];
        m.smooth = new float[m.vertexCount * 8];
        int o = 0;
        for (int t = 0; t < tris.size(); t++) {
            Vector3f fn = norm(new Vector3f(faceN[t]));
            for (int v : tris.get(t)) {
                Vector3f sn = norm(new Vector3f(sum.get(key(pos[v]))));
                put(m.flat, o, pos[v], fn, uv[v]);
                put(m.smooth, o, pos[v], sn, uv[v]);
                o += 8;
            }
        }
        return m;
    }

    /** Lantai: pos(3) normal(3) uv(2), CCW dilihat dari atas, normal (0,1,0). */
    public static float[] ground(float s, float repeat) {
        return new float[]{
            -s, 0,  s,  0, 1, 0,  0,      repeat,
             s, 0,  s,  0, 1, 0,  repeat, repeat,
             s, 0, -s,  0, 1, 0,  repeat, 0,
            -s, 0,  s,  0, 1, 0,  0,      repeat,
             s, 0, -s,  0, 1, 0,  repeat, 0,
            -s, 0, -s,  0, 1, 0,  0,      0
        };
    }

    private static String key(Vector3f p) {
        return String.format("%.4f,%.4f,%.4f", p.x, p.y, p.z);
    }

    private static Vector3f norm(Vector3f v) {
        return v.lengthSquared() > 1e-12f ? v.normalize() : v.set(0, 1, 0);
    }

    private static void put(float[] a, int o, Vector3f p, Vector3f n, Vector2f t) {
        a[o] = p.x;     a[o + 1] = p.y; a[o + 2] = p.z;
        a[o + 3] = n.x; a[o + 4] = n.y; a[o + 5] = n.z;
        a[o + 6] = t.x; a[o + 7] = t.y;
    }
}