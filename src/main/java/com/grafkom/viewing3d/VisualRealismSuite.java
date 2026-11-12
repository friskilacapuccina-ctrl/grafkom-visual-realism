package com.grafkom.viewing3d;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBImageWrite;

/**
 * 3D Visual Realism Suite (Minggu 11).
 *
 * Kontrol:
 *   Drag mouse kiri : orbit kamera     Scroll : zoom
 *   B : Phong <-> Blinn-Phong          F : Flat <-> Smooth
 *   Z : Z-Buffer on/off                C : Back-face culling on/off
 *   T : Texture Nearest <-> Mipmapped  H : Shadow on/off
 *   X : Tekstur on/off                 N : mode bias shadow
 *   Panah kiri/kanan : putar cahaya    R : reset kamera & cahaya
 *   P : PENGUJIAN OTOMATIS (screenshot + FPS ke folder hasil_uji)
 *   ESC : keluar
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

    // ===== pengujian otomatis =====
    private static class Step {
        final String name;
        final Runnable setup;
        Step(String name, Runnable setup) { this.name = name; this.setup = setup; }
    }

    private static final double WARM = 1.5;     // detik pemanasan sebelum mengukur
    private static final double MEASURE = 3.0;  // detik pengukuran FPS
    private boolean autoRunning = false;
    private List<Step> steps;
    private int stepIndex = 0;
    private double stepStart, measureStart;
    private int measureFrames;
    private final StringBuilder csv = new StringBuilder();
    private final File outDir = new File("hasil_uji");

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
            if (autoRunning && key != GLFW_KEY_ESCAPE) return;   // abaikan tombol saat uji otomatis
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
                case GLFW_KEY_T -> { if (press) setMipmap(!mipmapOn); }
                case GLFW_KEY_P -> { if (press) startAuto(); }
                case GLFW_KEY_R -> {
                    radius = 10.0f; azimuth = 30.0f; elevation = 25.0f; lightAngle = 45.0f;
                }
                default -> { }
            }
            updateTitle();
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (autoRunning) return;
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
            if (!dragging || autoRunning) return;
            azimuth -= (float) ((x - lastX) * 0.4);
            elevation = clamp(elevation + (float) ((y - lastY) * 0.4), 3.0f, 89.0f);
            lastX = x;
            lastY = y;
        });

        glfwSetScrollCallback(window, (w, xoff, yoff) -> {
            if (autoRunning) return;
            radius = clamp(radius - (float) yoff * 0.5f, 3.0f, 25.0f);
        });
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void setMipmap(boolean on) {
        mipmapOn = on;
        texture.setMipmapped(on);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            render();
            if (autoRunning) autoTick();   // dibaca sebelum swap supaya screenshot = frame terbaru
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
            HSRManager.setupDepthTest(true);
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
        HSRManager.setupDepthTest(zOn);
        HSRManager.setupBackFaceCulling(cullOn);

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

    private String modeString() {
        return String.format("%s | %s | Z %s | Cull %s | Filter %s | Tekstur %s | Shadow %s | Bias %s",
                blinnOn ? "Blinn-Phong" : "Phong",
                smoothOn ? "Smooth" : "Flat",
                zOn ? "ON" : "OFF",
                cullOn ? "ON" : "OFF",
                mipmapOn ? "Mipmap" : "Nearest",
                textureOn ? "ON" : "OFF",
                shadowOn ? "ON" : "OFF",
                BIAS_NAME[biasMode]);
    }

    private void updateTitle() {
        String auto = autoRunning
                ? String.format("[AUTO %d/%d %s] ", stepIndex + 1, steps.size(), steps.get(stepIndex).name)
                : "";
        glfwSetWindowTitle(window, auto + "Visual Realism | FPS " + fps + " | " + modeString());
    }

    // =====================================================================
    //  PENGUJIAN OTOMATIS: tombol P
    // =====================================================================

    private void applyBase() {
        blinnOn = true; smoothOn = true; zOn = true; cullOn = true;
        shadowOn = true; textureOn = true; biasMode = 0;
        radius = 10.0f; azimuth = 30.0f; elevation = 25.0f; lightAngle = 45.0f;
        setMipmap(true);
    }

    private List<Step> buildSteps() {
        return List.of(
            new Step("00_awal",          () -> { }),
            new Step("a_phong",          () -> { textureOn = false; blinnOn = false; }),
            new Step("a_blinn",          () -> { textureOn = false; }),
            new Step("b_flat",           () -> { textureOn = false; smoothOn = false; }),
            new Step("b_smooth",         () -> { textureOn = false; }),
            new Step("c_z_on_c_on",      () -> { shadowOn = false; textureOn = false; elevation = 8.0f; }),
            new Step("c_z_on_c_off",     () -> { shadowOn = false; textureOn = false; elevation = 8.0f; cullOn = false; }),
            new Step("c_z_off_c_on",     () -> { shadowOn = false; textureOn = false; elevation = 8.0f; zOn = false; }),
            new Step("c_z_off_c_off",    () -> { shadowOn = false; textureOn = false; elevation = 8.0f; zOn = false; cullOn = false; }),
            new Step("d_mipmap",         () -> { shadowOn = false; elevation = 8.0f; }),
            new Step("d_nearest",        () -> { shadowOn = false; elevation = 8.0f; setMipmap(false); }),
            new Step("e_shadow_on",      () -> { }),
            new Step("e_shadow_off",     () -> { shadowOn = false; }),
            new Step("e_bias_acne",      () -> { biasMode = 1; }),
            new Step("e_bias_besar",     () -> { biasMode = 2; })
        );
    }

    private void startAuto() {
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("Gagal membuat folder: " + outDir.getAbsolutePath());
            return;
        }
        steps = buildSteps();
        stepIndex = 0;
        csv.setLength(0);
        csv.append("langkah;fps;kondisi\n");
        autoRunning = true;
        System.out.println("=== PENGUJIAN OTOMATIS DIMULAI (jangan sentuh keyboard/mouse) ===");
        beginStep();
    }

    private void beginStep() {
        applyBase();
        steps.get(stepIndex).setup.run();
        stepStart = glfwGetTime();
        measureStart = -1;
        measureFrames = 0;
        updateTitle();
    }

    private void autoTick() {
        double now = glfwGetTime();
        double el = now - stepStart;

        if (measureStart < 0 && el >= WARM) {
            measureStart = now;
            measureFrames = 0;
        }
        if (measureStart >= 0) measureFrames++;

        if (el >= WARM + MEASURE) {
            Step s = steps.get(stepIndex);
            double f = measureFrames / (now - measureStart);
            saveScreenshot(s.name);
            csv.append(String.format(Locale.US, "%s;%.0f;%s%n", s.name, f, modeString()));
            System.out.printf(Locale.US, "%-16s %7.0f FPS   %s%n", s.name, f, modeString());

            stepIndex++;
            if (stepIndex >= steps.size()) {
                finishAuto();
            } else {
                beginStep();
            }
        }
    }

    private void finishAuto() {
        autoRunning = false;
        File csvFile = new File(outDir, "hasil_fps.csv");
        try {
            Files.writeString(csvFile.toPath(), csv.toString());
        } catch (IOException e) {
            System.err.println("Gagal menulis CSV: " + e.getMessage());
        }
        applyBase();
        updateTitle();
        System.out.println("=== SELESAI. Hasil ada di: " + outDir.getAbsolutePath() + " ===");
    }

    private void saveScreenshot(String name) {
        int[] fw = new int[1];
        int[] fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        int w = fw[0], h = fh[0];
        if (w == 0 || h == 0) return;

        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 3);
        glReadBuffer(GL_BACK);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, buf);

        // OpenGL membaca dari baris paling bawah, jadi balik urutan baris dulu
        ByteBuffer flipped = BufferUtils.createByteBuffer(w * h * 3);
        byte[] row = new byte[w * 3];
        for (int y = 0; y < h; y++) {
            buf.position((h - 1 - y) * w * 3);
            buf.get(row);
            flipped.put(row);
        }
        flipped.flip();

        File file = new File(outDir, name + ".png");
        if (!STBImageWrite.stbi_write_png(file.getAbsolutePath(), w, h, 3, flipped, w * 3)) {
            System.err.println("Gagal menyimpan screenshot: " + file.getAbsolutePath());
        }
    }
}