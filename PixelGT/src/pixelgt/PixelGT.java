/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pixelgt;

import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
    /**
     * This map maps area types as indicated in the XML file to
     * integer numbers, as indicated at
     * https://diuf.unifr.ch/main/hisdoc/icdar2017-hisdoc-layout-comp
     */
    static HashMap<String, Integer> nameToCode = new HashMap<>();
    static {
        nameToCode.put("background", 1);
        nameToCode.put("comment", 2);
        nameToCode.put("handwritten-annotation", 2);
        nameToCode.put("decoration", 4);
        nameToCode.put("textline", 8);
    }
    
    static int loadedImageWidth = 0;
    static int loadedImageHeight = 0;

    /**
     * The arguments are file names, first two as inputs, last two as outputs.
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, JDOMException {
        if (args.length<4) {
            System.err.println("Syntax:");
            System.err.println("\tjava -jar PixelGT.jar xmlFile image outBinary outGT");
            System.exit(1);
        }
        System.out.println("Starting the generation");
        // Due to a bug in an early version of this tool, this print has been
        // added to make sure everybody was using the newest version.
        System.out.println("\t(corrected version)");
        
        // Storing the parameters in meaningful variables.
        String xmlFileName = args[0];
        String inImageName = args[1];
        String outBinName = args[2];
        String outGTName = args[3];
        
        System.out.println("Loading image");
        BufferedImage ori = ImageIO.read(new File(inImageName));
        loadedImageWidth = ori.getWidth();
        loadedImageHeight = ori.getHeight();
        
        System.out.println("Binarizing");
        BufferedImage bin = Binarizer.binarize(ori);
        
        // Draws the polygons onto an image - does not take into account
        // ink vs background yet
        BufferedImage poly = drawPolygons(xmlFileName);
        
        // Merges a binary image and the polygons to produce the final
        // version of the labels - including the boundary pixels
        System.out.println("Merging polygones and binarized data");
        generateGroundTruth(poly, bin);
        
        // Output
        ImageIO.write(bin, "png", new File(outBinName));
        ImageIO.write(poly, "png", new File(outGTName));
    }
    
    /**
     * Combines the drawn polygons and the binary image to produce the ground
     * truth with the boundary pixels, and remove foreground outside of
     * polygons.
     * @param poly polygon image
     * @param bin binarized image
     */
    public static void generateGroundTruth(BufferedImage poly, BufferedImage bin) {
        // Storing into a variable for performance purpose
        int bgCode = nameToCode.get("background");
        for (int x=0; x<poly.getWidth(); x++) {
            for (int y=0; y<poly.getHeight(); y++) {
                // Get the labels of the polygon image (discard alpha channel)
                int code = poly.getRGB(x, y) & 0xFFFFFF;
                
                // Check if the binarization indicates there is foreground
                boolean fg = (bin.getRGB(x, y) & 0x1)==0;
                
                // Outside of polygons, so no content
                if (code==0x000000) {
                    // Paint binary image with background
                    bin.setRGB(x, y, 0xFFFFFF);
                    // Set GT image to background
                    poly.setRGB(x, y, bgCode);
                    continue;
                }
                
                // If there is no foreground detected but we are in a polygon (see
                // 'continue' just above), then we tag the pixel as boundary
                if (!fg) {
                    poly.setRGB(x,y, 0x800000 | code);
                }
            }
        }
    }
    
    /**
     * Draws the polygons from an XML file.
     * @param xmlFileName name of the XML file
     * @return an image
     * @throws JDOMException if the XML is invalid
     * @throws IOException  if the file cannot be read
     */
    public static BufferedImage drawPolygons(String xmlFileName) throws JDOMException, IOException {
        System.out.println("Opening XML file");
        SAXBuilder builder = new SAXBuilder();
        Document xml = builder.build(new File(xmlFileName));
        Element root = xml.getRootElement();
        Element page = getChild(root, "Page");
        
        int width = (page.getAttribute("imageWidth")==null) ? loadedImageWidth : Integer.parseInt(page.getAttributeValue("imageWidth"));
        int height = (page.getAttribute("imageHeight")==null) ? loadedImageHeight : Integer.parseInt(page.getAttributeValue("imageHeight"));
        BufferedImage res = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        
        
        List<Element> children = new LinkedList<>();
        children.addAll(getChildren(page, "TextRegion"));
        children.addAll(getChildren(page, "GraphicRegion"));
        System.out.println("Drawing "+children.size()+" polygons");
        for (Element tr : children) {
            String type = tr.getAttributeValue("type");
            if (!nameToCode.keySet().contains(type)) {
                System.err.println("Unknown region type: "+type);
                System.err.println("\tEither ignore it or add it to the static initialization block of PixelGT.java and recompile");
                continue;
            }
            drawPolygon(res, getChild(tr, "Coords"), nameToCode.get(type));
        }
        
        return res;
    }
    
    // Dirty but ignores namespaces
    private static Element getChild(Element parent, String name) {
        for (Element e : parent.getChildren()) {
            if (e.getName().equals(name)) {
                return e;
            }
        }
        return null;
    }
    
    private static List<Element> getChildren(Element parent, String name) {
        List<Element> res = new LinkedList<>();
        for (Element e : parent.getChildren()) {
            if (e.getName().equals(name)) {
                res.add(e);
            }
        }
        return res;
    }
    
    /**
     * Draws a polygon onto an image
     * @param image target image
     * @param coords jdom2 element containing the coordinates
     * @param mask binary mask to use (see link at description of nameToCode)
     */
    public static void drawPolygon(BufferedImage image, Element coords, int mask) {
        if (coords==null) {
            throw new Error("no Coords tage found");
        }
        Polygon poly = new Polygon();
        if (coords.getChildren().isEmpty()) {
            String ptsStr = coords.getAttributeValue("points");
            for (String ptStr : ptsStr.split(" ")) {
                String[] c = ptStr.split(",");
                int x = Integer.parseInt(c[0]);
                int y = Integer.parseInt(c[1]);
                poly.addPoint(x, y);
            }
        } else {
            for (Element pt : coords.getChildren()) {
                int x = Integer.parseInt(pt.getAttributeValue("x"));
                int y = Integer.parseInt(pt.getAttributeValue("y"));
                poly.addPoint(x, y);
            }
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
