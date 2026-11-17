import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** 把一张照片生成 Android 各密度的启动图标 + 自适应图标前景层 */
public class IconGen {

    public static void main(String[] args) throws Exception {
        File src = new File(args[0]);
        File res = new File(args[1]);
        BufferedImage img = ImageIO.read(src);
        System.out.println("source=" + img.getWidth() + "x" + img.getHeight());

        // 正方形中心裁剪：人像照片的视觉中心略偏上，取 48% 高度处
        int side = Math.min(img.getWidth(), img.getHeight());
        int cx = img.getWidth() / 2;
        int cy = (int) (img.getHeight() * 0.48);
        int x0 = Math.max(0, Math.min(img.getWidth() - side, cx - side / 2));
        int y0 = Math.max(0, Math.min(img.getHeight() - side, cy - side / 2));
        BufferedImage square = img.getSubimage(x0, y0, side, side);
        System.out.println("crop=(" + x0 + "," + y0 + "," + side + ")  avg=#" + avgHex(square));

        String[] dpis = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] legacy = {48, 72, 96, 144, 192};
        int[] fg = {108, 162, 216, 324, 432};

        for (int i = 0; i < dpis.length; i++) {
            File d = new File(res, "mipmap-" + dpis[i]);
            if (!d.exists() && !d.mkdirs()) {
                throw new IllegalStateException("mkdir failed: " + d);
            }
            write(scale(square, legacy[i], true), new File(d, "ic_launcher.png"));
            write(foreground(square, fg[i]), new File(d, "ic_launcher_foreground.png"));
            System.out.println("ok " + dpis[i] + " legacy=" + legacy[i] + " fg=" + fg[i]);
        }
    }

    /** 等比缩放并高质量插值；round=true 时加轻微圆角（老系统图标用） */
    static BufferedImage scale(BufferedImage src, int size, boolean round) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (round) {
            int r = Math.max(2, (int) (size * 0.16));
            g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, size, size, r, r));
        }
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    /**
     * 自适应图标前景层：108dp 画布，内容只占中间 72dp（约 66.7%），
     * 外面留透明——这样无论系统用圆形、方圆还是水滴遮罩都不会切到脸。
     */
    static BufferedImage foreground(BufferedImage src, int canvas) {
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        int inner = Math.round(canvas * 0.667f);
        int off = (canvas - inner) / 2;
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 四角轻微圆角，叠在圆形遮罩里更自然
        int r = Math.max(3, (int) (inner * 0.22));
        g.setClip(new java.awt.geom.RoundRectangle2D.Float(off, off, inner, inner, r, r));
        g.drawImage(src, off, off, inner, inner, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, File f) throws Exception {
        ImageIO.write(img, "png", f);
    }

    static String avgHex(BufferedImage img) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                int p = img.getRGB(x, y);
                r += (p >> 16) & 0xff;
                g += (p >> 8) & 0xff;
                b += p & 0xff;
                n++;
            }
        }
        if (n == 0) {
            return "334155";
        }
        return String.format("%02X%02X%02X", r / n, g / n, b / n);
    }
}
