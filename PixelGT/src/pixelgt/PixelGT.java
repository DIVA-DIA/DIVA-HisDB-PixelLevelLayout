/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pixelgt;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import javax.imageio.ImageIO;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

/**
 *
 * @author Mathias Seuret
 */
public class PixelGT {
    
    static HashMap<String, Integer> nameToCode = new HashMap<>();
    static {
        nameToCode.put("background", 1);
        nameToCode.put("comment", 2);
        nameToCode.put("decoration", 4);
        nameToCode.put("textline", 8);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, JDOMException {
        if (args.length<4) {
            System.err.println("Syntax:");
            System.err.println("\tjava -jar PixelGT.jar xmlFile image outBinary outGT");
            System.exit(1);
        }
        System.out.println("Starting the generation");
        System.out.println("\t(corrected version)");
        
        String xmlFileName = args[0];
        String inImageName = args[1];
        String outBinName = args[2];
        String outGTName = args[3];
        
        System.out.println("Loading image");
        BufferedImage ori = ImageIO.read(new File(inImageName));
        
        System.out.println("Binarizing");
        BufferedImage bin = Binarizer.binarize(ori);
        
        BufferedImage poly = drawPolygons(xmlFileName);
        
        System.out.println("Merging polygones and binarized data");
        generateGroundTruth(poly, bin);
        
        ImageIO.write(bin, "png", new File(outBinName));
        ImageIO.write(poly, "png", new File(outGTName));
    }
    
    public static void generateGroundTruth(BufferedImage poly, BufferedImage bin) {
        int bgCode = nameToCode.get("background");
        for (int x=0; x<poly.getWidth(); x++) {
            for (int y=0; y<poly.getHeight(); y++) {
                int code = poly.getRGB(x, y) & 0xFFFFFF;
                boolean fg = (bin.getRGB(x, y) & 0x1)==0;
                
                // Nothing there
                if (code==0x000000) {
                    bin.setRGB(x, y, 0xFFFFFF);
                    poly.setRGB(x, y, bgCode);
                    continue;
                }
                
                // boundary
                if (!fg) {
                    poly.setRGB(x,y, 0x800000 | code);
                }
            }
        }
    }
    
    public static BufferedImage drawPolygons(String xmlFileName) throws JDOMException, IOException {
        System.out.println("Opening XML file");
        SAXBuilder builder = new SAXBuilder();
        Document xml = builder.build(new File(xmlFileName));
        Element root = xml.getRootElement();
        Element page = root.getChild("Page");
        
        int width = Integer.parseInt(page.getAttributeValue("imageWidth"));
        int height = Integer.parseInt(page.getAttributeValue("imageHeight"));
        BufferedImage res = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        System.out.println("Drawing "+page.getChildren("TextRegion").size()+" polygons");
        for (Element tr : page.getChildren("TextRegion")) {
            String type = tr.getAttributeValue("type");
            if (!nameToCode.keySet().contains(type)) {
                throw new Error("Unknown region type: "+type);
            }
            drawPolygon(res, tr.getChild("Coords"), nameToCode.get(type));
        }
        
        return res;
    }
    
    public static void drawPolygon(BufferedImage image, Element coords, int mask) {
        if (coords==null) {
            throw new Error("no Coords tage found");
        }
        Polygon poly = new Polygon();
        for (Element pt : coords.getChildren()) {
            int x = Integer.parseInt(pt.getAttributeValue("x"));
            int y = Integer.parseInt(pt.getAttributeValue("y"));
            poly.addPoint(x, y);
        }
        for (int x=poly.getBounds().x; x<=poly.getBounds().x+poly.getBounds().width; x++) {
            for (int y=poly.getBounds().y; y<=poly.getBounds().y+poly.getBounds().height; y++) {
                if (!poly.contains(x, y) || x<0 || y<0 || x>=image.getWidth() || y>=image.getHeight()) {
                    continue;
                }
                int rgb = image.getRGB(x,y) | mask;
                image.setRGB(x, y, rgb);
            }
        }
    }
}
