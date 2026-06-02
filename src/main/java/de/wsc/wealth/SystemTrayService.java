package de.wsc.wealth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

@Component
public class SystemTrayService {

    private final ApplicationContext applicationContext;
    private final int serverPort;

    public SystemTrayService(ApplicationContext applicationContext,
                             @Value("${server.port:8080}") int serverPort) {
        this.applicationContext = applicationContext;
        this.serverPort = serverPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initTrayIcon() {
        if (!SystemTray.isSupported()) return;

        String version = Wealth.class.getPackage().getImplementationVersion();
        if (version == null) version = "dev";

        TrayIcon trayIcon = new TrayIcon(createIcon(), "Wealth " + version);
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = new PopupMenu();
        MenuItem versionItem = new MenuItem("Version " + version);
        versionItem.setEnabled(false);
        popup.add(versionItem);
        popup.addSeparator();
        MenuItem infoItem = new MenuItem("Info...");
        infoItem.addActionListener(e -> openBrowser("http://localhost:" + serverPort + "/info"));
        popup.add(infoItem);
        popup.addSeparator();
        MenuItem exitItem = new MenuItem("Beenden");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        });
        popup.add(exitItem);
        trayIcon.setPopupMenu(popup);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ignored) {
        }
    }

    private void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception ignored) {}
    }

    private Image createIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(34, 139, 34));
        g.fillOval(0, 0, size - 1, size - 1);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 9));
        g.drawString("W", 3, 11);
        g.dispose();
        return image;
    }
}
