package com.codeflow.examples;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Paketlenmiş örnek kaynaklar ({@code src/main/resources/examples/}).
 * Uygulama kodundan ayrı tutulur; JAR içinde classpath üzerinden okunur.
 */
public final class ExampleSources {

    private static final String DEFAULT_DEMO = "/examples/ecommerce-cart.java";

    private ExampleSources() {
    }

    /** Açılışta editörde gösterilen varsayılan e-ticaret sepeti demosu. */
    public static String loadDefaultCartDemo() {
        try (InputStream in = ExampleSources.class.getResourceAsStream(DEFAULT_DEMO)) {
            if (in == null) {
                return "// Örnek kaynak bulunamadı: " + DEFAULT_DEMO + "\n";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "// Örnek kaynak okunamadı.\n";
        }
    }
}
