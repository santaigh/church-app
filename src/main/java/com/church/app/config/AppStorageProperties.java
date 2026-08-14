package com.church.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bound from {@code app.storage.*}. */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class AppStorageProperties {

    /**
     * Directory holding the per-church logos, one file per church named
     * {@code <church_id>.png}.
     *
     * <p>Deliberately outside {@code src/main/resources}: anything there is packaged into
     * the jar, which is read-only at runtime -- the same trap that keeps
     * {@code application-local.yml} at the project root.
     *
     * <p>The directory is never mapped as a static resource root. Serving it directly
     * would make {@code /logos/2.png} guessable, letting anyone enumerate the tenants;
     * every read goes through {@code /church/logo}, which resolves the church from the
     * signed-in user instead of from the URL.
     */
    private String logoDir = "./data/logos";
}
