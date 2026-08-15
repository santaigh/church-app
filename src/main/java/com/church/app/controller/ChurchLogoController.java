package com.church.app.controller;

import com.church.app.security.TenantContext;
import com.church.app.service.ChurchLogoService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.WebRequest;

/**
 * Serves the signed-in user's church logo.
 *
 * <p>There is deliberately no church id in the path. {@code /church/{id}/logo} would let
 * any signed-in user walk the ids and harvest the tenant list, and it is exactly the kind
 * of primary-key lookup that Hibernate's tenant filter does not cover -- the trap
 * {@code TenantAwareRepositoryImpl} exists to work around. Resolving the church from the
 * principal removes the parameter that could be tampered with.
 */
@Controller
public class ChurchLogoController {

    private final ChurchLogoService churchLogoService;

    public ChurchLogoController(ChurchLogoService churchLogoService) {
        this.churchLogoService = churchLogoService;
    }

    @GetMapping("/church/logo")
    public ResponseEntity<Resource> logo(WebRequest request) {
        // The tenant scope, not the principal: a platform user working inside a parish
        // has no church of their own, and must still see that parish's crest. The scope
        // is the one place that already knows which church this request belongs to.
        Long churchId = TenantContext.currentChurchId().orElse(null);

        ChurchLogoService.ChurchLogo logo = churchLogoService.resolve(churchId);

        // Returning null after checkNotModified is the documented Spring pattern: the
        // response has already been completed as a 304.
        if (logo.lastModified() > 0 && request.checkNotModified(logo.lastModified())) {
            return null;
        }

        return ResponseEntity.ok()
                // private, because this is one tenant's image and must never be held by a
                // shared cache; no-cache means "revalidate", not "do not store", so a
                // replaced logo appears immediately rather than after a browser cache expires.
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(logo.mediaType())
                .body(logo.resource());
    }
}
