import java.awt.EventQueue;

/** Entry point. */
public class SnakeGame {
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> new Game().open());
    }
}
