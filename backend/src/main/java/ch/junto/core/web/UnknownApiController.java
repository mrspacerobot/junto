package ch.junto.core.web;

import ch.junto.core.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ensures an authenticated request to an unknown API endpoint remains an API 404. */
@RestController
public class UnknownApiController {

    @RequestMapping("/api/{*path}")
    public void notFound(HttpServletRequest request) {
        throw new NotFoundException(
            "No API route for " + request.getMethod() + " " + request.getRequestURI());
    }
}
