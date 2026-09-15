package com.vksiv.personal.apps.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular bundle that :frontend packs into BOOT-INF/classes/static, and
 * falls back to index.html for unknown paths so client-side routes such as
 * /notes survive a page refresh instead of returning 404.
 *
 * Paths under /api and /actuator are deliberately excluded from the fallback:
 * an unknown API route must return a real 404, not the HTML shell.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String ASSET_ROOT = "classpath:/static/";
    private static final String INDEX = "/static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(ASSET_ROOT)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource(INDEX);
                        // Absent when the backend is run with -PskipFrontend, which is
                        // the normal case during local development.
                        return index.exists() ? index : null;
                    }
                });
    }
}
