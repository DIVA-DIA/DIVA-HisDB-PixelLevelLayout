/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pixelgt;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.openimaj.image.FImage;
import org.openimaj.image.ImageUtilities;
import org.openimaj.image.processing.convolution.FGaussianConvolve;

/**
 *
 * @author Mathias Seuret
 */
public class Binarizer {
    public static BufferedImage binarize(BufferedImage ori) {
        return binarize(ori, 25.0f, 1.5f, 0.01f);
    }
    
    public static BufferedImage binarize(BufferedImage ori, float g1, float g2, float th) {
        FImage fbi = ImageUtilities.createFImage(ori);
        FImage img = fbi.process(new FGaussianConvolve(g1)).subtract(fbi.process(new FGaussianConvolve(g2)));
        img = img.threshold(th);
        BufferedImage bin = ImageUtilities.createBufferedImage(img);
        BufferedImage bi = new BufferedImage(ori.getWidth(), ori.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < ori.getWidth(); x++) {
            for (int y = 0; y < ori.getHeight(); y++) {
                if ((bin.getRGB(x, y)&0xFF)!=0) {
                    bi.setRGB(x, y, 0x000000);
                } else {
                    bi.setRGB(x, y, 0xFFFFFF);
                }
            }
        }
        return bi;
    }
    
}
