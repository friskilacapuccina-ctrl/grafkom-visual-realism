package com.grafkom.viewing3d;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/**
 * 3D Visual Realism Suite (Minggu 11).
 *
 * Kontrol:
 *   Drag mouse kiri : orbit kamera     Scroll : zoom
 *   B : Phong <-> Blinn-Phong          F : Flat <-> Smooth
 *   Z : Z-Buffer on/off                C : Back-face culling on/off
 *   T : Texture Nearest <-> Mipmapped  H : Shadow on/off
 *   X : Tekstur on/off                 N : mode bias shadow (normal/acne/peter panning)
 *   Panah kiri/kanan : putar posisi cahaya     R : reset kamera & cahaya     ESC : keluar
 */
public class VisualRealismSuite {

    private long window;

    private Camera3D camera;
    private ShaderProgram phong, depth;
    private ShadowMapFBO shadowFBO;
    private Texture2D texture;
    private Mesh sphereFlat, sphereSmooth, ground;

    // status toggle
    private boolean blinnOn = true;
    private boolean smoothOn = true;
    private boolean zOn = true;
    private boolean cullOn = true;
    private boolean mipmapOn = true;
    private boolean shadowOn = true;
    private boolean textureOn = true;
    private int biasMode = 0;
    private static final String[] BIAS_NAME = {"normal", "tanpa bias (acne)", "bias besar (peter panning)"};
    private static final float[] BIAS_SLOPE = {0.05f, 0.0f, 0.3f};
    private static final float[] BIAS_MIN = {0.005f, 0.0f, 0.1f};

    // kamera orbit
    private float radius = 10.0f, azimuth = 30.0f, elevation = 25.0f;
    private boolean dragging = false;
    private double lastX, lastY;

    // cahaya
    private float lightAngle = 45.0f;
    private final Vector3f lightPos = new Vector3f();

    // FPS
    private double fpsTimer = 0;
    private int frames = 0, fps = 0;

    public static void main(String[] args) {
        new VisualRealismSuite().run();
    }

    private void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Gagal menginisialisasi GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(1024, 640, "3D Visual Realism Suite", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Gagal membuat window GLFW (cek driver GPU / OpenGL 3.3)");
        }

        setupCallbacks();

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);   // VSync OFF supaya angka FPS bermakna untuk perbandingan
        glfwShowWindow(window);
        GL.createCapabilities();

        phong = new ShaderProgram("/shaders/phong.vert", "/shaders/phong.frag");
        depth = new ShaderProgram("/shaders/depth.vert", "/shaders/depth.frag");
        shadowFBO = new ShadowMapFBO();
        texture = new Texture2D("src/main/resources/textures/checker.png");

        MeshBuilder.MeshData sph = MeshBuilder.sphere(16, 24, 1.0f);
        sphereFlat = new Mesh(sph.flat);
        sphereSmooth = new Mesh(sph.smooth);
        ground = new Mesh(MeshBuilder.ground(8f, 10f));

        camera = new Camera3D(new Vector3f(0, 1, radius), new Vector3f(0, 1, 0), new Vector3f(0, 1, 0));

        glEnable(GL_DEPTH_TEST);
        glClearColor(0.12f, 0.12f, 0.14f, 1.0f);
        updateTitle();
    }

    private void setupCallbacks() {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            boolean press = (action == GLFW_PRESS);
            switch (key) {
                case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(w, true);
                case GLFW_KEY_LEFT -> lightAngle -= 3.0f;
                case GLFW_KEY_RIGHT -> lightAngle += 3.0f;
                case GLFW_KEY_B -> { if (press) blinnOn = !blinnOn; }
                case GLFW_KEY_F -> { if (press) smoothOn = !smoothOn; }
                case GLFW_KEY_Z -> { if (press) zOn = !zOn; }
                case GLFW_KEY_C -> { if (press) cullOn = !cullOn; }
                case GLFW_KEY_H -> { if (press) shadowOn = !shadowOn; }
                case GLFW_KEY_X -> { if (press) textureOn = !textureOn; }
                case GLFW_KEY_N -> { if (press) biasMode = (biasMode + 1) % BIAS_NAME.length; }
                case GLFW_KEY_T -> {
                    if (press) {
                        mipmapOn = !mipmapOn;
                        texture.setMipmapped(mipmapOn);
                    }
                }
                case GLFW_KEY_R -> {
                    radius = 10.0f; azimuth = 30.0f; elevation = 25.0f; lightAngle = 45.0f;
                }
                default -> { }
            }
            updateTitle();
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                dragging = (action == GLFW_PRESS);
                if (dragging) {
                    double[] x = new double[1];
                    double[] y = new double[1];
                    glfwGetCursorPos(w, x, y);
                    lastX = x[0];
                    lastY = y[0];
                }
            }
        });

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (!dragging) return;
            azimuth -= (float) ((x - lastX) * 0.4);
            elevation = clamp(elevation + (float) ((y - lastY) * 0.4), 3.0f, 89.0f);
            lastX = x;
            lastY = y;
        });

        glfwSetScrollCallback(window, (w, xoff, yoff) ->
                radius = clamp(radius - (float) yoff * 0.5f, 3.0f, 25.0f));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            double now = glfwGetTime();
            if (now - fpsTimer >= 1.0) {
                fps = frames;
                frames = 0;
                fpsTimer = now;
                updateTitle();
            }
        }
    }

    private void render() {
        int[] fw = new int[1];
        int[] fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        if (fw[0] == 0 || fh[0] == 0) return;   // window di-minimize

        // posisi cahaya + matriks ruang cahaya
        double a = Math.toRadians(lightAngle);
        lightPos.set((float) (5.0 * Math.cos(a)), 6.0f, (float) (5.0 * Math.sin(a)));

        Matrix4f lightProj = new Matrix4f().ortho(-10, 10, -10, 10, 1.0f, 25.0f);
        Matrix4f lightView = new Matrix4f().lookAt(lightPos, new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        Matrix4f lightSpace = new Matrix4f(lightProj).mul(lightView);

        Matrix4f sphereModel = new Matrix4f().translate(0.0f, 1.5f, 0.0f);
        Matrix4f groundModel = new Matrix4f();
        Mesh sphere = smoothOn ? sphereSmooth : sphereFlat;

        // ===== PASS 1: depth dari sudut pandang cahaya =====
        if (shadowOn) {
            HSRManager.setupDepthTest(true);        // selalu ON di pass ini
            HSRManager.setupBackFaceCulling(false);
            shadowFBO.bindForWriting();
            depth.use();
            depth.setMat4("lightSpaceMatrix", lightSpace);
            depth.setMat4("model", sphereModel);
            sphere.draw();
            depth.setMat4("model", groundModel);
            ground.draw();
            shadowFBO.unbind(fw[0], fh[0]);
        }

        // ===== PASS 2: render normal dari kamera =====
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        HSRManager.setupDepthTest(zOn);             // ikut tombol Z
        HSRManager.setupBackFaceCulling(cullOn);    // ikut tombol C

        camera.updateOrbitalPosition(radius, azimuth, elevation);
        Matrix4f view = camera.calculateLookAtMatrix();
        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(45.0), (float) fw[0] / fh[0], 0.1f, 100.0f);

        phong.use();
        phong.setMat4("view", view);
        phong.setMat4("projection", proj);
        phong.setMat4("lightSpaceMatrix", lightSpace);
        phong.setVec3("lightPos", lightPos);
        phong.setVec3("viewPos", camera.getEye());
        phong.setVec3("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        phong.setBool("useBlinnPhong", blinnOn);
        phong.setBool("useShadow", shadowOn);
        phong.setBool("useTexture", textureOn);
        phong.setFloat("biasSlope", BIAS_SLOPE[biasMode]);
        phong.setFloat("biasMin", BIAS_MIN[biasMode]);
        phong.setInt("textureSampler", 0);
        phong.setInt("shadowMap", 1);
        texture.bind(0);
        shadowFBO.bindDepthTexture(1);

        phong.setMat4("model", sphereModel);
        phong.setVec3("objectColor", new Vector3f(0.9f, 0.5f, 0.2f));
        sphere.draw();

        phong.setMat4("model", groundModel);
        phong.setVec3("objectColor", new Vector3f(0.6f, 0.6f, 0.65f));
        ground.draw();
    }

    private void updateTitle() {
        glfwSetWindowTitle(window, String.format(
                "Visual Realism | FPS %d | %s | %s | Z %s | Cull %s | Filter %s | Tekstur %s | Shadow %s | Bias %s",
                fps,
                blinnOn ? "Blinn-Phong" : "Phong",
                smoothOn ? "Smooth" : "Flat",
                zOn ? "ON" : "OFF",
                cullOn ? "ON" : "OFF",
                mipmapOn ? "Mipmap" : "Nearest",
                textureOn ? "ON" : "OFF",
                shadowOn ? "ON" : "OFF",
                BIAS_NAME[biasMode]));
    }
}