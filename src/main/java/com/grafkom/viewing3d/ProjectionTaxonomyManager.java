package com.grafkom.viewing3d;

import static org.lwjgl.opengl.GL11.*;

/**
 * Pengelola taksonomi proyeksi. Setiap mode = jenis proyeksi + preset posisi kamera.
 * Dengan begitu rotasi tidak bertumpuk dengan kamera orbital.
 */
public class ProjectionTaxonomyManager {

    public enum ProjectionType {
        ORTHOGRAPHIC(0f, 0f),
        ISOMETRIC(45f, 35.264f),
        OBLIQUE_CAVALIER(0f, 0f),
        PERSPECTIVE_1POINT(0f, 0f),
        PERSPECTIVE_2POINT(40f, 0f),
        PERSPECTIVE_3POINT(40f, 30f);

        public final float presetAzimuth;
        public final float presetElevation;

        ProjectionType(float presetAzimuth, float presetElevation) {
            this.presetAzimuth = presetAzimuth;
            this.presetElevation = presetElevation;
        }

        public boolean isPerspective() {
            return this == PERSPECTIVE_1POINT
                || this == PERSPECTIVE_2POINT
                || this == PERSPECTIVE_3POINT;
        }
    }

    public static void applyProjection(ProjectionType type, float aspect,
                                       PerspectiveProjection persp, float orthoSize) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        if (type.isPerspective()) {
            // Matriks perspektif dari kelas PerspectiveProjection (dihitung eksplisit)
            glLoadMatrixf(persp.calculateProjectionMatrix().get(new float[16]));
            return;
        }

        glOrtho(-orthoSize * aspect, orthoSize * aspect, -orthoSize, orthoSize, 0.1, 100.0);

        if (type == ProjectionType.OBLIQUE_CAVALIER) {
            applyShear(1.0f, 45.0f);   // k = 1.0 (Cavalier); pakai 0.5 untuk Cabinet
        }
    }

    /** Matriks shear terhadap sumbu z: x' = x - k*cos(a)*z, y' = y - k*sin(a)*z. */
    private static void applyShear(float k, float angleDeg) {
        float a = (float) Math.toRadians(angleDeg);
        glMultMatrixf(new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            -k * (float) Math.cos(a), -k * (float) Math.sin(a), 1, 0,
            0, 0, 0, 1
        });
    }
}