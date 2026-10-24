package com.seeloggyplus.app;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class StorePackageCli {

    private static final String FOUNDATION = "http://schemas.microsoft.com/appx/manifest/foundation/windows10";
    private static final String UAP = "http://schemas.microsoft.com/appx/manifest/uap/windows10";
    private static final String RESTRICTED =
            "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities";

    private StorePackageCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Expected: directory version name publisher displayName");
        String version = args[1];
        if (!version.matches("\\d+\\.\\d+\\.\\d+")) throw new IllegalArgumentException("Store builds require major.minor.patch");
        for (String part : version.split("\\.")) {
            if (part.length() > 5 || Integer.parseInt(part) > 65535)
                throw new IllegalArgumentException("MSIX version components must be at most 65535");
        }
        Path output = Path.of(args[0]);
        Files.createDirectories(output.resolve("Assets"));
        try (var input = Objects.requireNonNull(StorePackageCli.class.getResourceAsStream("/packaging/StoreLauncher.manifest"))) {
            String nativeManifest = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("@VERSION@", version + ".0");
            Files.writeString(output.resolve("StoreLauncher.manifest"), nativeManifest, StandardCharsets.UTF_8);
        }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().newDocument();
        Element root = document.createElementNS(FOUNDATION, "Package");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:uap", UAP);
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:rescap", RESTRICTED);
        root.setAttribute("IgnorableNamespaces", "uap rescap");
        document.appendChild(root);
        Element identity = child(root, "Identity");
        identity.setAttribute("Name", args[2]);
        identity.setAttribute("Publisher", args[3]);
        identity.setAttribute("Version", version + ".0");
        identity.setAttribute("ProcessorArchitecture", "x64");
        Element properties = child(root, "Properties");
        child(properties, "DisplayName").setTextContent("SeeLoggyPlus");
        child(properties, "PublisherDisplayName").setTextContent(args[4]);
        child(properties, "Logo").setTextContent("Assets\\Logo50.png");
        Element device = child(child(root, "Dependencies"), "TargetDeviceFamily");
        device.setAttribute("Name", "Windows.Desktop");
        device.setAttribute("MinVersion", "10.0.17763.0");
        device.setAttribute("MaxVersionTested", "10.0.26100.0");
        child(child(root, "Resources"), "Resource").setAttribute("Language", "en-us");
        Element application = child(child(root, "Applications"), "Application");
        application.setAttribute("Id", "SeeLoggyPlus");
        application.setAttribute("Executable", "SeeLoggyPlus.exe");
        application.setAttribute("EntryPoint", "Windows.FullTrustApplication");
        Element visuals = document.createElementNS(UAP, "uap:VisualElements");
        application.appendChild(visuals);
        visuals.setAttribute("DisplayName", "SeeLoggyPlus");
        visuals.setAttribute("Description", "Desktop log viewer for local files and SSH servers");
        visuals.setAttribute("BackgroundColor", "transparent");
        visuals.setAttribute("Square44x44Logo", "Assets\\Logo44.png");
        visuals.setAttribute("Square150x150Logo", "Assets\\Logo150.png");
        Element capability = document.createElementNS(RESTRICTED, "rescap:Capability");
        capability.setAttribute("Name", "runFullTrust");
        child(root, "Capabilities").appendChild(capability);
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(output.resolve("AppxManifest.xml").toFile()));
        try (var input = Objects.requireNonNull(StorePackageCli.class.getResourceAsStream("/images/app-icon.png"))) {
            BufferedImage icon = ImageIO.read(input);
            for (int size : new int[]{44, 50, 150}) {
                var logo = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                var graphics = logo.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = (double) size / Math.max(icon.getWidth(), icon.getHeight());
                int width = Math.max(1, (int) Math.round(icon.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(icon.getHeight() * scale));
                graphics.drawImage(icon, (size - width) / 2, (size - height) / 2, width, height, null);
                graphics.dispose();
                ImageIO.write(logo, "png", output.resolve("Assets/Logo" + size + ".png").toFile());
            }
        }
    }

    private static Element child(Element parent, String name) {
        Element element = parent.getOwnerDocument().createElementNS(FOUNDATION, name);
        parent.appendChild(element);
        return element;
    }
}
