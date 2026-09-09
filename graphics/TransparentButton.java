package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/**
 * Modern glassmorphic button with transparent background, rounded corners,
 * smooth hover effects, and crisp typography.
 */
public class TransparentButton extends JButton {
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int cornerRadius = 24;

    private Color normalBg = new Color(255, 255, 255, 16);
    private Color hoverBg = new Color(0, 230, 118, 40);
    private Color pressedBg = new Color(0, 230, 118, 80);

    private Color normalBorder = new Color(255, 255, 255, 60);
    private Color hoverBorder = new Color(0, 230, 118, 220);

    private Color normalText = Color.WHITE;
    private Color hoverText = new Color(0, 255, 140);

    public TransparentButton(String text) {
        super(text);
        init();
    }

    public TransparentButton(String text, int cornerRadius) {
        super(text);
        this.cornerRadius = cornerRadius;
        init();
    }

    private void init() {
        setFont(UITheme.FONT_BUTTON);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                isPressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public void setColors(Color normalBg, Color hoverBg, Color normalBorder, Color hoverBorder, Color normalText, Color hoverText) {
        this.normalBg = normalBg;
        this.hoverBg = hoverBg;
        this.normalBorder = normalBorder;
        this.hoverBorder = hoverBorder;
        this.normalText = normalText;
        this.hoverText = hoverText;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        UITheme.applyQualityRendering(g2d);

        int width = getWidth();
        int height = getHeight();

        // Background
        Color bg = isPressed ? pressedBg : (isHovered ? hoverBg : normalBg);
        g2d.setColor(bg);
        g2d.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius);

        // Border
        Color border = isHovered ? hoverBorder : normalBorder;
        g2d.setColor(border);
        g2d.setStroke(new BasicStroke(isHovered ? 2.0f : 1.2f));
        g2d.drawRoundRect(1, 1, width - 2, height - 2, cornerRadius, cornerRadius);

        // Text
        Color textColor = isHovered ? hoverText : normalText;
        g2d.setColor(textColor);
        g2d.setFont(getFont());
        FontMetrics fm = g2d.getFontMetrics();
        int textX = (width - fm.stringWidth(getText())) / 2;
        int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(getText(), textX, textY);

        g2d.dispose();
    }
}
