package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

import org.joml.Matrix4f;

/**
 * Stereo Red-Cyan Anaglyph memakai Off-Axis Projection (Asymmetric Frustum)
 * dan Color Masking. Mata kiri = kanal merah, mata kanan = kanal cyan (hijau + biru).
 */
public class StereoAnaglyphRenderer {

    private float ipd = 0.2f;           // Interpupillary Distance (satuan dunia)
    private float focalLength = 8.0f;   // jarak bidang parallax nol (zero parallax) dari kamera

    public void renderAnaglyph(Matrix4f view, PerspectiveProjection p, Runnable renderSceneCallback) {
        // Bersihkan SEMUA kanal terlebih dahulu (sebelum color mask dipasang)
        glColorMask(true, true, true, true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // PASS 1: mata kiri -> hanya kanal merah
        renderEye(-1, view, p, renderSceneCallback, true, false, false);

        // PASS 2: mata kanan -> kanal hijau + biru (cyan); depth dibersihkan, warna di-blend
        glClear(GL_DEPTH_BUFFER_BIT);
        renderEye(+1, view, p, renderSceneCallback, false, true, true);

        // Kembalikan color mask normal
        glColorMask(true, true, true, true);
    }

    /** side: -1 = mata kiri, +1 = mata kanan. */
    private void renderEye(int side, Matrix4f view, PerspectiveProjection p,
                           Runnable scene, boolean r, boolean g, boolean b) {
        glColorMask(r, g, b, true);

        double near = p.getZNear();
        double far = p.getZFar();
        double top = near * Math.tan(Math.toRadians(p.getFovDegree()) / 2.0);
        double right = top * p.getAspectRatio();
        double shift = (ipd / 2.0) * near / focalLength;   // pergeseran frustum (off-axis)

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glFrustum(-right - side * shift, right - side * shift, -top, top, near, far);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(-side * ipd / 2.0f, 0.0f, 0.0f);      // geser posisi mata di ruang kamera
        glMultMatrixf(view.get(new float[16]));            // lalu terapkan kamera orbital

        scene.run();
    }

    public float getIpd() { return ipd; }
    public void setIpd(float ipd) { this.ipd = Math.max(0.0f, Math.min(1.0f, ipd)); }
}