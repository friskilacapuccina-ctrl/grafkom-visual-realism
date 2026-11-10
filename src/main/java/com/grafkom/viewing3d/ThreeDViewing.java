package com.grafkom.viewing3d;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.grafkom.viewing3d.ProjectionTaxonomyManager.ProjectionType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/**
 * 3D Viewing Interactive Visualizer.
 *
 * Kontrol:
 *   Drag mouse kiri : orbit kamera        Scroll : zoom (radius)
 *   1-6             : mode proyeksi (Ortho, Isometric, Cavalier, 1/2/3-point)
 *   S               : toggle stereo anaglyph (merah-cyan)
 *   Panah atas/bawah (atau + / -) : FOV     [ / ] : IPD stereo
 *   R               : reset      ESC : keluar
 */
public class ThreeDViewing {

    private long window;

    private Camera3D camera;
    private PerspectiveProjection projection;
    private StereoAnaglyphRenderer stereo;
    private Mesh3D mesh;

    private ProjectionType mode = ProjectionType.PERSPECTIVE_3POINT;
    private boolean stereoOn = false;

    private float radius = 8.0f;
    private float azimuth = mode.presetAzimuth;
    private float elevation = mode.presetElevation;
    private float fov = 45.0f;

    private boolean dragging = false;
    private double lastX, lastY;

    public static void main(String[] args) {
        new ThreeDViewing().run();
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

        window = glfwCreateWindow(1024, 640, "3D Viewing", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Gagal membuat window GLFW");
        }

        setupCallbacks();

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);

        camera = new Camera3D(new Vector3f(0, 0, radius), new Vector3f(0, 0, 0), new Vector3f(0, 1, 0));
        projection = new PerspectiveProjection(fov, 1024f / 640f, 0.1f, 100.0f);
        stereo = new StereoAnaglyphRenderer();
        mesh = new Mesh3D();
    }

    private void setupCallbacks() {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) return;
            switch (key) {
                case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(w, true);
                case GLFW_KEY_1 -> setMode(ProjectionType.ORTHOGRAPHIC);
                case GLFW_KEY_2 -> setMode(ProjectionType.ISOMETRIC);
                case GLFW_KEY_3 -> setMode(ProjectionType.OBLIQUE_CAVALIER);
                case GLFW_KEY_4 -> setMode(ProjectionType.PERSPECTIVE_1POINT);
                case GLFW_KEY_5 -> setMode(ProjectionType.PERSPECTIVE_2POINT);
                case GLFW_KEY_6 -> setMode(ProjectionType.PERSPECTIVE_3POINT);
                case GLFW_KEY_S -> {
                    if (action == GLFW_PRESS) toggleStereo();
                }
                case GLFW_KEY_UP, GLFW_KEY_EQUAL -> fov = clamp(fov - 2.0f, 20.0f, 120.0f);
                case GLFW_KEY_DOWN, GLFW_KEY_MINUS -> fov = clamp(fov + 2.0f, 20.0f, 120.0f);
                case GLFW_KEY_LEFT_BRACKET -> stereo.setIpd(stereo.getIpd() - 0.02f);
                case GLFW_KEY_RIGHT_BRACKET -> stereo.setIpd(stereo.getIpd() + 0.02f);
                case GLFW_KEY_R -> resetView();
                default -> { }
            }
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
            elevation = clamp(elevation + (float) ((y - lastY) * 0.4), -89.0f, 89.0f);
            lastX = x;
            lastY = y;
        });

        glfwSetScrollCallback(window, (w, xoff, yoff) ->
            radius = clamp(radius - (float) yoff * 0.5f, 2.0f, 20.0f));
    }

    private void setMode(ProjectionType type) {
        mode = type;
        azimuth = type.presetAzimuth;
        elevation = type.presetElevation;
        if (!type.isPerspective()) {
            stereoOn = false;   // anaglyph hanya untuk proyeksi perspektif
        }
    }

    private void toggleStereo() {
        stereoOn = !stereoOn;
        if (stereoOn && !mode.isPerspective()) {
            setMode(ProjectionType.PERSPECTIVE_3POINT);
        }
    }

    private void resetView() {
        radius = 8.0f;
        fov = 45.0f;
        stereoOn = false;
        setMode(ProjectionType.PERSPECTIVE_3POINT);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void render() {
        int[] fw = new int[1];
        int[] fh = new int[1];
        glfwGetFramebufferSize(window, fw, fh);
        if (fw[0] == 0 || fh[0] == 0) return;   // window di-minimize

        float aspect = (float) fw[0] / fh[0];
        glViewport(0, 0, fw[0], fh[0]);

        projection.setAspectRatio(aspect);
        projection.setFovDegree(fov);

        camera.updateOrbitalPosition(radius, azimuth, elevation);
        Matrix4f view = camera.calculateLookAtMatrix();

        if (stereoOn) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);   // latar hitam supaya anaglyph bersih
            stereo.renderAnaglyph(view, projection, this::drawScene);
        } else {
            glClearColor(0.12f, 0.12f, 0.14f, 1.0f);
            glColorMask(true, true, true, true);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            ProjectionTaxonomyManager.applyProjection(mode, aspect, projection, radius * 0.4f);

            glMatrixMode(GL_MODELVIEW);
            glLoadMatrixf(view.get(new float[16]));
            drawScene();
        }

        glfwSetWindowTitle(window, String.format(
            "3D Viewing | %s | FOV %.0f | Stereo %s | IPD %.2f",
            mode, fov, stereoOn ? "ON" : "OFF", stereo.getIpd()));
    }

    /** Isi scene: grid lantai (petunjuk kedalaman) + Color Cube. Tidak mengubah matriks. */
    private void drawScene() {
        glLineWidth(1.0f);
        glBegin(GL_LINES);
        glColor3f(0.40f, 0.40f, 0.45f);
        for (int i = -6; i <= 6; i++) {
            glVertex3f(i, -1.5f, -6);
            glVertex3f(i, -1.5f, 6);
            glVertex3f(-6, -1.5f, i);
            glVertex3f(6, -1.5f, i);
        }
        glEnd();

        mesh.draw();
    }
}