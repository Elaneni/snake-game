package graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import logic.SnakeEngine;

/**
 * Renders the snake with interpolated smooth positions, drop shadows,
 * scale patterns, and directional eyes.
 */
public class SnakeRenderer {

    public void draw(Graphics2D g2d, SnakeEngine engine, Color headColor, Color bodyColor) {
        int bodyParts = engine.getBodyParts();
        double[] renderX = engine.getRenderX();
        double[] renderY = engine.getRenderY();
        char direction = engine.getDirection();
        int unitSize = SnakeEngine.UNIT_SIZE;

        // 1. Drop shadow pass behind the whole snake
        g2d.setColor(new Color(0, 0, 0, 75));
        for (int i = 0; i < bodyParts; i++) {
            int sx = (int) renderX[i] + 4;
            int sy = (int) renderY[i] + 6;
            g2d.fillRoundRect(sx, sy, unitSize, unitSize, unitSize / 2, unitSize / 2);
        }

        // 2. Snake segments
        for (int i = 0; i < bodyParts; i++) {
            int posX = (int) renderX[i];
            int posY = (int) renderY[i];

            Color startColor = (i == 0) ? headColor : bodyColor;
            Color endColor = startColor.darker();
            GradientPaint gradient = new GradientPaint(
                posX, posY, startColor,
                posX + unitSize, posY + unitSize, endColor
            );
            g2d.setPaint(gradient);

            int arcSize = unitSize / 2;
            g2d.fillRoundRect(posX, posY, unitSize, unitSize, arcSize, arcSize);

            // Subtle highlight rim
            g2d.setColor(new Color(255, 255, 255, 45));
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(posX + 1, posY + 1, unitSize - 2, unitSize - 2, arcSize, arcSize);

            // Scales on body
            if (i != 0) {
                g2d.setColor(endColor.brighter());
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawLine(posX + 6, posY + unitSize - 6, posX + unitSize - 6, posY + 6);
            }

            // Eyes and pupils on head
            if (i == 0) {
                drawEyes(g2d, posX, posY, unitSize, direction);
            }
        }
    }

    private void drawEyes(Graphics2D g2d, int posX, int posY, int unitSize, char direction) {
        g2d.setColor(Color.WHITE);
        int eyeSize = unitSize / 4;
        int pupilSize = eyeSize / 2;
        int offset = unitSize / 4;

        int leftEyeX = posX, leftEyeY = posY, rightEyeX = posX, rightEyeY = posY;
        int leftPupilX = posX, leftPupilY = posY, rightPupilX = posX, rightPupilY = posY;

        switch (direction) {
            case 'R':
                leftEyeX = posX + unitSize - offset - eyeSize;
                leftEyeY = posY + offset;
                rightEyeX = posX + unitSize - offset - eyeSize;
                rightEyeY = posY + unitSize - offset - eyeSize;
                leftPupilX = leftEyeX + eyeSize - pupilSize;
                leftPupilY = leftEyeY + pupilSize / 2;
                rightPupilX = rightEyeX + eyeSize - pupilSize;
                rightPupilY = rightEyeY + pupilSize / 2;
                break;
            case 'L':
                leftEyeX = posX + offset;
                leftEyeY = posY + offset;
                rightEyeX = posX + offset;
                rightEyeY = posY + unitSize - offset - eyeSize;
                leftPupilX = leftEyeX;
                leftPupilY = leftEyeY + pupilSize / 2;
                rightPupilX = rightEyeX;
                rightPupilY = rightEyeY + pupilSize / 2;
                break;
            case 'U':
                leftEyeX = posX + offset;
                leftEyeY = posY + offset;
                rightEyeX = posX + unitSize - offset - eyeSize;
                rightEyeY = posY + offset;
                leftPupilX = leftEyeX + pupilSize / 2;
                leftPupilY = leftEyeY;
                rightPupilX = rightEyeX + pupilSize / 2;
                rightPupilY = rightEyeY;
                break;
            case 'D':
                leftEyeX = posX + offset;
                leftEyeY = posY + unitSize - offset - eyeSize;
                rightEyeX = posX + unitSize - offset - eyeSize;
                rightEyeY = posY + unitSize - offset - eyeSize;
                leftPupilX = leftEyeX + pupilSize / 2;
                leftPupilY = leftEyeY + eyeSize - pupilSize;
                rightPupilX = rightEyeX + pupilSize / 2;
                rightPupilY = rightEyeY + eyeSize - pupilSize;
                break;
        }

        // Eyeballs
        g2d.fillOval(leftEyeX, leftEyeY, eyeSize, eyeSize);
        g2d.fillOval(rightEyeX, rightEyeY, eyeSize, eyeSize);

        // Pupils
        g2d.setColor(Color.BLACK);
        g2d.fillOval(leftPupilX, leftPupilY, pupilSize, pupilSize);
        g2d.fillOval(rightPupilX, rightPupilY, pupilSize, pupilSize);
    }
}
