package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Model 3D Color Cube. Hanya berisi data geometri dan cara menggambarnya;
 * tidak ada kode kamera/proyeksi di sini.
 */
public class Mesh3D {

    private final float[][] vertices = {
        {-1, -1,  1}, { 1, -1,  1}, { 1,  1,  1}, {-1,  1,  1},   // 0-3 depan (z = +1)
        {-1, -1, -1}, { 1, -1, -1}, { 1,  1, -1}, {-1,  1, -1}    // 4-7 belakang (z = -1)
    };

    private final int[][] faces = {
        {0, 1, 2, 3},   // depan
        {5, 4, 7, 6},   // belakang
        {4, 0, 3, 7},   // kiri
        {1, 5, 6, 2},   // kanan
        {3, 2, 6, 7},   // atas
        {4, 5, 1, 0}    // bawah
    };

    // Warna pastel (semua kanal RGB > 0) supaya tetap terlihat di kedua mata saat anaglyph
    private final float[][] colors = {
        {0.90f, 0.40f, 0.40f},
        {0.40f, 0.90f, 0.40f},
        {0.40f, 0.40f, 0.90f},
        {0.90f, 0.90f, 0.40f},
        {0.90f, 0.40f, 0.90f},
        {0.40f, 0.90f, 0.90f}
    };

    public void draw() {
        // Sisi berwarna
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.0f, 1.0f);
        glBegin(GL_QUADS);
        for (int f = 0; f < faces.length; f++) {
            float[] c = colors[f];
            glColor3f(c[0], c[1], c[2]);
            for (int idx : faces[f]) {
                float[] v = vertices[idx];
                glVertex3f(v[0], v[1], v[2]);
            }
        }
        glEnd();
        glDisable(GL_POLYGON_OFFSET_FILL);

        // Garis tepi putih
        glColor3f(1.0f, 1.0f, 1.0f);
        glLineWidth(2.0f);
        for (int[] face : faces) {
            glBegin(GL_LINE_LOOP);
            for (int idx : face) {
                float[] v = vertices[idx];
                glVertex3f(v[0], v[1], v[2]);
            }
            glEnd();
        }
    }
}