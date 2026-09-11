import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Play feature graphic: 1024x500, brand blue, launcher mark plus wordmark and one line of what it is. */
public class Feature {
    /** Largest size at or below `start` that fits `text` in `maxWidth` — no line is ever allowed to run off the edge. */
    private static Font fit(Graphics2D g, String text, String family, int style, int start, int maxWidth) {
        for (int size = start; size > 10; size--) {
            Font f = new Font(family, style, size);
            if (g.getFontMetrics(f).stringWidth(text) <= maxWidth) return f;
        }
        return new Font(family, style, 10);
    }

    public static void main(String[] a) throws Exception {
        int W = 1024, H = 500, MARGIN = 64;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Ground: the brand blue, deepened towards the bottom so the panel has depth without
        // becoming a gradient anyone notices.
        g.setPaint(new GradientPaint(0, 0, new Color(0x1C7AD1), 0, H, new Color(0x115694)));
        g.fillRect(0, 0, W, H);

        // One wide, low-contrast arc, echoing the sweep under the aircraft in the mark.
        g.setColor(new Color(255, 255, 255, 16));
        g.fillOval(-300, 170, 1180, 920);

        BufferedImage fg = ImageIO.read(new File(a[0]));
        int mark = 268, mx = 72, my = (H - mark) / 2;
        g.drawImage(fg, mx, my, mark, mark, null);

        int tx = mx + mark + 40;
        int avail = W - tx - MARGIN;

        String name = "AMlog", l1 = "Maintenance logbook and CRS generator", l2 = "for EASA Part-66 certifying staff";
        Font fName = fit(g, name, "Segoe UI Semibold", Font.BOLD, 104, avail);
        Font fLine = fit(g, l1, "Segoe UI", Font.PLAIN, 34, avail);
        if (g.getFontMetrics(fLine).stringWidth(l2) > avail) fLine = fit(g, l2, "Segoe UI", Font.PLAIN, 34, avail);

        g.setFont(fName);
        g.setColor(Color.WHITE);
        g.drawString(name, tx, 238);
        g.setFont(fLine);
        int lead = g.getFontMetrics(fLine).getHeight() + 6;
        g.setColor(new Color(255, 255, 255, 238));
        g.drawString(l1, tx, 300);
        g.setColor(new Color(255, 255, 255, 186));
        g.drawString(l2, tx, 300 + lead);

        g.dispose();
        ImageIO.write(img, "png", new File(a[1]));
        System.out.printf("wrote %s — name %dpt, lines %dpt, widest line %dpx in %dpx available%n",
                a[1], fName.getSize(), fLine.getSize(),
                Math.max(img.createGraphics().getFontMetrics(fLine).stringWidth(l1),
                         img.createGraphics().getFontMetrics(fLine).stringWidth(l2)), avail);
    }
}
