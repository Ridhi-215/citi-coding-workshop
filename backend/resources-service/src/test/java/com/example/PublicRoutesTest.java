package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.common.http.Router;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every route of resources-service requires a token except this explicit list.
 */
class PublicRoutesTest {

    @Test
    void onlyExpectedRoutesArePublic() {
        Router router = Handler.routes(new Router("resources-service", () -> null, () -> { }));
        assertEquals(List.of("GET /health"), router.publicRoutes());
    }
}
