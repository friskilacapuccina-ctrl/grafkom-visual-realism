package com.grafkom.viewing3d;

import org.joml.Matrix4f;

/**
 * Proyeksi perspektif dengan viewing frustum eksplisit (FOV_y, aspect, near, far).
 */
public class PerspectiveProjection {

    private float fovDegree;
    private float aspectRatio;
    private float zNear;
    private float zFar;

    public PerspectiveProjection(float fovDegree, float aspectRatio, float zNear, float zFar) {
        this.fovDegree = fovDegree;
        this.aspectRatio = aspectRatio;
        this.zNear = zNear;
        this.zFar = zFar;
    }

    public Matrix4f calculateProjectionMatrix() {
        float fovRad = (float) Math.toRadians(fovDegree);
        float f = 1.0f / (float) Math.tan(fovRad / 2.0f);

        Matrix4f proj = new Matrix4f();
        proj.zero();
        proj.m00(f / aspectRatio);
        proj.m11(f);
        proj.m22(-(zFar + zNear) / (zFar - zNear));
        proj.m32(-(2.0f * zFar * zNear) / (zFar - zNear));
        proj.m23(-1.0f);
        proj.m33(0.0f);
        return proj;
    }

    // Setter: FOV dibatasi agar tidak ekstrem (Dolly Zoom / tuning interaktif)
    public void setFovDegree(float fovDegree) {
        this.fovDegree = Math.max(20.0f, Math.min(120.0f, fovDegree));
    }

    public void setAspectRatio(float aspectRatio) { this.aspectRatio = aspectRatio; }

    public float getFovDegree() { return fovDegree; }
    public float getAspectRatio() { return aspectRatio; }
    public float getZNear() { return zNear; }
    public float getZFar() { return zFar; }
}