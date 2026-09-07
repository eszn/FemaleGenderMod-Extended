import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

/** Small hidden-window check using Minecraft's existing native libraries. */
public class GraphicsProbe {
    public static void main(String[] args) {
        GLFWErrorCallback.createPrint(System.err).set();
        System.out.println("Initializing GLFW");
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        System.out.println("Creating hidden OpenGL context");
        long window = GLFW.glfwCreateWindow(32, 32, "Graphics verification", 0, 0);
        if (window == 0) throw new IllegalStateException("Context creation failed");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("PASS: " + GL11.glGetString(GL11.GL_RENDERER));
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }
}
