package com.codeflow.app.sample;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Yalnızca {@code codeflow-visualizer-app} modülündeki paketlenmiş örnek kaynaklar.
 * Kütüphane ({@code codeflow-core}, {@code codeflow-swing-ui}) bu sınıfa bağımlı değildir.
 */
public final class ExampleSources {

    private static final String DEFAULT_DEMO = "/examples/ecommerce-cart.java";

    private ExampleSources() {
    }

    /** Bağımsız uygulama açılışında editörde gösterilen varsayılan e-ticaret sepeti demosu. */
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
