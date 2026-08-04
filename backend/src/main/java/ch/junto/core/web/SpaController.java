package ch.junto.core.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the uncached SPA shell only for routes owned by the Vue router.
 * API, WebSocket, and unknown resource paths are deliberately not catch-all routes.
 */
@Controller
public class SpaController {

    private final Resource index = new ClassPathResource("static/index.html");

    @GetMapping(value = {
        "/",
        "/auth/login",
        "/auth/verify",
        "/auth/onboard",
        "/activities",
        "/activities/{id}",
        "/explore",
        "/saved",
        "/messages",
        "/messages/{activityId}",
        "/profile"
    }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noStore())
            .body(index);
    }
}
