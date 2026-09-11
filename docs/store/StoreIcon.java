import com.android.ide.common.vectordrawable.VdPreview;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/** Builds the 512x512 Play store icon: brand-blue ground plus the adaptive icon's foreground. */
public class StoreIcon {
    public static void main(String[] a) throws Exception {
        int N = 512;
        BufferedImage out = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.decode(a[0]));
        g.fillRect(0, 0, N, N);
        BufferedImage fg;
        if (a[1].endsWith(".xml")) {
            fg = VdPreview.getPreviewFromVectorXml(VdPreview.TargetSize.createFromMaxDimension(N),
                    new String(Files.readAllBytes(new File(a[1]).toPath()), "UTF-8"), new StringBuilder());
        } else {
            fg = ImageIO.read(new File(a[1]));
        }
        g.drawImage(fg, 0, 0, N, N, null);
        g.dispose();
        ImageIO.write(out, "png", new File(a[2]));
        System.out.println("wrote " + a[2] + " from " + new File(a[1]).getName());
    }
}
