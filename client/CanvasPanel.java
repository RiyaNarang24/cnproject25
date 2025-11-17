import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;


public class CanvasPanel extends JPanel {

    public enum ToolMode { PENCIL, ERASER, LINE, RECTANGLE }

    private ToolMode currentMode = ToolMode.PENCIL;

    private BufferedImage canvasImage;
    private Graphics2D canvasG;

    private Color currentColor = Color.BLACK;

    private int stroke = 3;
    private int eraserStroke = 10;

    private int startX, startY;
    private boolean isDragging = false;
    private int currentX, currentY;

    private int prevX = -1, prevY = -1;

    private List<String> history = new ArrayList<>();
    private Map<String, long[]> activeDrawers = new ConcurrentHashMap<>();

    public interface DrawListener {
        void sendDrawCommand(String cmd);
    }

    private DrawListener listener;


    // -----------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------
    public CanvasPanel(int width, int height) {
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));

        setBackground(Color.WHITE);
        setOpaque(true);

        initCanvas(width, height);

        MouseAdapter ma = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                isDragging = true;
                startX = e.getX();
                startY = e.getY();
                currentX = startX;
                currentY = startY;

                if (currentMode == ToolMode.PENCIL || currentMode == ToolMode.ERASER) {
                    prevX = startX;
                    prevY = startY;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                currentX = e.getX();
                currentY = e.getY();

                if (currentMode == ToolMode.PENCIL) {
                    drawSegment(prevX, prevY, currentX, currentY, currentColor, stroke, true);
                    prevX = currentX;
                    prevY = currentY;
                }
                else if (currentMode == ToolMode.ERASER) {
                    drawSegment(prevX, prevY, currentX, currentY, Color.WHITE, eraserStroke, true);
                    prevX = currentX;
                    prevY = currentY;
                }
                else {
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
                int endX = e.getX();
                int endY = e.getY();

                if (currentMode == ToolMode.LINE) {
                    drawFinalLine(startX, startY, endX, endY, currentColor, stroke, true);
                }
                else if (currentMode == ToolMode.RECTANGLE) {
                    drawFinalRect(startX, startY, endX, endY, currentColor, stroke, true);
                }

                prevX = -1;
                prevY = -1;
                startX = startY = currentX = currentY = 0;

                repaint();
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);

        new javax.swing.Timer(500, e -> repaint()).start();
    }


    // -----------------------------------------------------------
    // Initialize Canvas Buffer
    // -----------------------------------------------------------
    private void initCanvas(int w, int h) {
        canvasImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvasImage.createGraphics();
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, w, h);
        canvasG.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }


    // -----------------------------------------------------------
    // Setters
    // -----------------------------------------------------------
    public void setDrawListener(DrawListener l) { this.listener = l; }

    public void setColor(Color c) { this.currentColor = c; }

    public void setToolMode(ToolMode mode) { this.currentMode = mode; }

    public void setStrokeWidth(int s) { this.stroke = s; }

    public void setEraserStrokeWidth(int s) { this.eraserStroke = s; }


    // -----------------------------------------------------------
    // Clear + Undo
    // -----------------------------------------------------------
    public void clearCanvas(boolean send) {
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());
        repaint();
        history.clear();

        if (send && listener != null) {
            listener.sendDrawCommand("CLEAR:");
        }
    }

    public void undo() {
        if (history.isEmpty()) return;

        history.remove(history.size() - 1);

        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, canvasImage.getWidth(), canvasImage.getHeight());

        for (String cmd : history) {
            applyDrawCommandLocal(cmd, false);
        }

        repaint();
    }


    // -----------------------------------------------------------
    // Apply DRAW updates from Server
    // -----------------------------------------------------------
    public void applyDrawCommandLocal(String cmd, boolean store) {

        try {
            String[] parts = cmd.split(":", 3);
            if (parts.length < 3) return;

            String type = parts[0];
            String username = parts[1];
            String payload = parts[2];

            switch (type) {

                case "DRAW": {
                    String[] f = payload.split(",");
                    if (f.length < 8) return;

                    int x1 = Integer.parseInt(f[0]);
                    int y1 = Integer.parseInt(f[1]);
                    int x2 = Integer.parseInt(f[2]);
                    int y2 = Integer.parseInt(f[3]);
                    int r = Integer.parseInt(f[4]);
                    int g = Integer.parseInt(f[5]);
                    int b = Integer.parseInt(f[6]);
                    int st = Integer.parseInt(f[7]);

                    long[] info = new long[]{ x2, y2, System.currentTimeMillis() };
                    activeDrawers.put(username, info);

                    drawSegment(x1, y1, x2, y2, new Color(r,g,b), st, false);
                    break;
                }

                case "LINE": {
                    String[] f = payload.split(",");
                    if (f.length < 8) return;

                    int x1 = Integer.parseInt(f[0]);
                    int y1 = Integer.parseInt(f[1]);
                    int x2 = Integer.parseInt(f[2]);
                    int y2 = Integer.parseInt(f[3]);
                    int r = Integer.parseInt(f[4]);
                    int g = Integer.parseInt(f[5]);
                    int b = Integer.parseInt(f[6]);
                    int st = Integer.parseInt(f[7]);
activeDrawers.put(username, new long[]{ x2, y2, System.currentTimeMillis() });


                    drawFinalLine(x1, y1, x2, y2, new Color(r,g,b), st, false);
                    break;
                }

                case "RECT": {
                    String[] f = payload.split(",");
                    if (f.length < 8) return;

                    int x = Integer.parseInt(f[0]);
                    int y = Integer.parseInt(f[1]);
                    int w = Integer.parseInt(f[2]);
                    int h = Integer.parseInt(f[3]);
                    int r = Integer.parseInt(f[4]);
                    int g = Integer.parseInt(f[5]);
                    int b = Integer.parseInt(f[6]);
                    int st = Integer.parseInt(f[7]);

                    // Convert back to two corner points
                    activeDrawers.put(username, new long[]{ x, y, System.currentTimeMillis() });

                    drawFinalRect(x, y, x + w, y + h, new Color(r,g,b), st, false);
                    break;
                }
            }

            if (store) history.add(cmd);

        } catch (Exception ex) {
            System.out.println("Bad DRAW cmd: " + ex.getMessage());
        }
    }


    // -----------------------------------------------------------
    // DRAWING METHODS (PENCIL / LINE / RECT)
    // -----------------------------------------------------------
    private void drawSegment(int x1, int y1, int x2, int y2, Color c, int st, boolean send) {

        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(c);
        canvasG.drawLine(x1, y1, x2, y2);

        repaint();

        if (send && listener != null) {
            String payload = x1+","+y1+","+x2+","+y2+","
                    +c.getRed()+","+c.getGreen()+","+c.getBlue()+","+st;

            listener.sendDrawCommand("DRAW:" + payload);
            history.add("DRAW:self:" + payload);
        }
    }


    private void drawFinalLine(int x1, int y1, int x2, int y2, Color c, int st, boolean send) {

        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(c);
        canvasG.drawLine(x1, y1, x2, y2);

        repaint();

        if (send && listener != null) {
            String payload = x1+","+y1+","+x2+","+y2+","
                    +c.getRed()+","+c.getGreen()+","+c.getBlue()+","+st;

            listener.sendDrawCommand("LINE:" + payload);
            history.add("LINE:self:" + payload);
        }
    }


    // ⭐ FIXED RECTANGLE (Always uses top-left + width + height)
    private void drawFinalRect(int x1, int y1, int x2, int y2, Color c, int st, boolean send) {

        int x = Math.min(x1, x2);
        int y = Math.min(y1, y2);
        int w = Math.abs(x1 - x2);
        int h = Math.abs(y1 - y2);

        canvasG.setStroke(new BasicStroke(st, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.setColor(c);
        canvasG.drawRect(x, y, w, h);

        repaint();

        if (send && listener != null) {
            String payload =
                    x+","+y+","+w+","+h+","+
                    c.getRed()+","+c.getGreen()+","+c.getBlue()+","+st;

            listener.sendDrawCommand("RECT:" + payload);
            history.add("RECT:self:" + payload);
        }
    }


    // -----------------------------------------------------------
    // Painting
    // -----------------------------------------------------------
    @Override
@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    // Draw saved canvas
    g.drawImage(canvasImage, 0, 0, null);

    Graphics2D g2 = (Graphics2D) g;

    // --- LIVE PREVIEW (line / rectangle) ---
    if (isDragging) {
        g2.setColor(currentColor);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (currentMode == ToolMode.LINE) {
            g2.drawLine(startX, startY, currentX, currentY);
        }
        else if (currentMode == ToolMode.RECTANGLE) {
            int x = Math.min(startX, currentX);
            int y = Math.min(startY, currentY);
            int w = Math.abs(startX - currentX);
            int h = Math.abs(startY - currentY);
            g2.drawRect(x, y, w, h);
        }
    }

    // --- USERNAME CURSOR LABELS ---
    long now = System.currentTimeMillis();
    Graphics2D g2User = (Graphics2D) g;
    g2User.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2User.setFont(new Font("Arial", Font.BOLD, 12));

    Iterator<Map.Entry<String, long[]>> it = activeDrawers.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<String, long[]> entry = it.next();
        long[] info = entry.getValue();
        long time = info[2];

        if (now - time > 1500) {
            it.remove(); // remove stale cursor
        } else {
            String user = entry.getKey();
            int x = (int) info[0];
            int y = (int) info[1];

            g2User.setColor(Color.BLACK);
            g2User.drawString(user, x + 6, y + 16);

            g2User.setColor(Color.WHITE);
            g2User.drawString(user, x + 5, y + 15);
        }
    }
}
}
    }
}
