package org.example.Shading;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class ShaderViewerLWJGL {

    private long window;
    private final String userFragmentShaderCode;

    // Shader program and uniform locations
    private int shaderProgram;
    private int uResolutionLocation;
    private int uTimeLocation;
    private int uMouseLocation;

    // Geometry for a full-screen quad
    private int vao, vbo, ebo;

    private static final String VERTEX_SHADER_SOURCE =
            "#version 330 core\n" +
                    "layout (location = 0) in vec2 aPos;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_TEMPLATE =
            "#version 330 core\n" +
                    "out vec4 FragColor;\n" +
                    "uniform vec2 u_resolution;\n" +
                    "uniform float u_time;\n" +
                    "uniform vec2 u_mouse;\n" +
                    "void main() {\n" +
                    "    vec2 uv = gl_FragCoord.xy / u_resolution.xy;\n" +
                    "    vec2 mouse = u_mouse.xy / u_resolution.xy;\n" +
                    "    vec3 color = vec3(0.0);\n" +
                    "    // --- User GLSL code starts here ---\n" +
                    "    %s\n" + // User code will be injected here //TODO: add something that converts glsl code to format we could use
                    "    // --- User GLSL code ends here ---\n" +
                    "    FragColor = vec4(color, 1.0);\n" +
                    "}\n";

    private ShaderViewerLWJGL(String userCode) {
        this.userFragmentShaderCode = String.format(FRAGMENT_SHADER_TEMPLATE, userCode);
    }

    public static void launch(String userCode) {
        new ShaderViewerLWJGL(userCode).run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Setup an error callback.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        // Create the window
        window = glfwCreateWindow(800, 600, "LWJGL Shader Viewer", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Center the window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // Enable v-sync
        glfwShowWindow(window);
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        GL.createCapabilities();

        setupShaders();
        setupQuad();

        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        long startTime = System.currentTimeMillis();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glUseProgram(shaderProgram);

            // Update uniforms
            long currentTime = System.currentTimeMillis();
            float time = (currentTime - startTime) / 1000.0f;
            glUniform1f(uTimeLocation, time);

            try (MemoryStack stack = stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                glfwGetWindowSize(window, width, height);
                glUniform2f(uResolutionLocation, width.get(0), height.get(0));

                DoubleBuffer xpos = stack.mallocDouble(1);
                DoubleBuffer ypos = stack.mallocDouble(1);
                glfwGetCursorPos(window, xpos, ypos);
                // Invert Y because screen and GL coordinates are opposite
                glUniform2f(uMouseLocation, (float) xpos.get(0), height.get(0) - (float) ypos.get(0));
            }

            // Draw the quad
            glBindVertexArray(vao);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);
            glBindVertexArray(0);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void setupShaders() {
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, VERTEX_SHADER_SOURCE);
        glCompileShader(vertexShader);
        checkShaderCompilation(vertexShader);

        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, userFragmentShaderCode);
        glCompileShader(fragmentShader);
        checkShaderCompilation(fragmentShader);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);
        glLinkProgram(shaderProgram);
        checkProgramLinking(shaderProgram);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);

        // Get uniform locations
        uResolutionLocation = glGetUniformLocation(shaderProgram, "u_resolution");
        uTimeLocation = glGetUniformLocation(shaderProgram, "u_time");
        uMouseLocation = glGetUniformLocation(shaderProgram, "u_mouse");
    }

    private void setupQuad() {
        float[] vertices = {
                1.0f,  1.0f, // top right
                1.0f, -1.0f, // bottom right
                -1.0f, -1.0f, // bottom left
                -1.0f,  1.0f  // top left
        };
        int[] indices = {
                0, 1, 3, // first triangle
                1, 2, 3  // second triangle
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        ebo = glGenBuffers();

        glBindVertexArray(vao);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }


    private void cleanup() {
        // Free resources
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteProgram(shaderProgram);

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    // Utility methods for checking shader errors
    private void checkShaderCompilation(int shader) {
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("Shader compilation error: " + glGetShaderInfoLog(shader, 512));
            System.err.println("\n--- Shader Source ---\n" + glGetShaderSource(shader));
            throw new RuntimeException("Shader compilation failed.");
        }
    }

    private void checkProgramLinking(int program) {
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            System.err.println("Program linking error: " + glGetProgramInfoLog(program, 512));
            throw new RuntimeException("Shader program linking failed.");
        }
    }
}