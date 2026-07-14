package de.wsc.wealth.license;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone developer tool to generate the Ed25519 signing keypair (once) and mint license
 * keys. Deliberately NOT a Spring bean — it never runs as part of the shipped application.
 *
 * The private key lives outside this repository at ~/.wealth/license-signing-key.properties and
 * must stay secret; only the printed public key belongs in LicenseService.PUBLIC_KEY_BASE64.
 *
 * Usage (after `mvn compile`):
 *   java -cp target/classes de.wsc.wealth.license.LicenseKeyGenerator
 *   java -cp target/classes de.wsc.wealth.license.LicenseKeyGenerator --features=COINS,CUSTOM_CRITERIA --expires=2027-12-31
 */
public final class LicenseKeyGenerator {

    private static final Path KEY_FILE =
        Path.of(System.getProperty("user.home"), ".wealth", "license-signing-key.properties");

    private LicenseKeyGenerator() {}

    public static void main(String[] args) throws IOException {
        KeyPair keyPair = loadOrCreateKeyPair();
        System.out.println("Public key (embed in LicenseService.PUBLIC_KEY_BASE64):");
        System.out.println(LicenseCodec.encodePublicKey(keyPair.getPublic()));
        System.out.println();

        String featuresArg = argValue(args, "--features");
        if (featuresArg == null) {
            System.out.println("Signing key ready at " + KEY_FILE + ".");
            System.out.println("Pass --features=COINS,CUSTOM_CRITERIA [--expires=YYYY-MM-DD] to mint a license key.");
            return;
        }
        Set<String> features = Arrays.stream(featuresArg.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        String expiresArg = argValue(args, "--expires");
        LocalDate expires = expiresArg != null && !expiresArg.isBlank() ? LocalDate.parse(expiresArg) : null;

        System.out.println("License key:");
        System.out.println(LicenseCodec.sign(features, expires, keyPair.getPrivate()));
    }

    private static String argValue(String[] args, String name) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) return arg.substring(name.length() + 1);
        }
        return null;
    }

    private static KeyPair loadOrCreateKeyPair() throws IOException {
        if (Files.exists(KEY_FILE)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(KEY_FILE)) {
                props.load(in);
            }
            PublicKey publicKey = LicenseCodec.decodePublicKey(props.getProperty("publicKey"));
            PrivateKey privateKey = LicenseCodec.decodePrivateKey(props.getProperty("privateKey"));
            return new KeyPair(publicKey, privateKey);
        }

        KeyPair keyPair = LicenseCodec.generateKeyPair();
        Properties props = new Properties();
        props.setProperty("publicKey", LicenseCodec.encodePublicKey(keyPair.getPublic()));
        props.setProperty("privateKey", LicenseCodec.encodePrivateKey(keyPair.getPrivate()));
        Files.createDirectories(KEY_FILE.getParent());
        try (OutputStream out = Files.newOutputStream(KEY_FILE)) {
            props.store(out, "Wealth license signing key - KEEP SECRET, DO NOT COMMIT");
        }
        System.out.println("Generated new signing keypair at " + KEY_FILE);
        return keyPair;
    }
}
