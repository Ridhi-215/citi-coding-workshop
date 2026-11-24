package com.example.common.http;

import java.util.Map;

/**
 * A request handler registered on a {@link Router}.
 */
@FunctionalInterface
public interface Route {

    /**
     * Handles a request.
     *
     * @param request parsed request, with path parameters and principal set
     * @return Lambda response map, usually built with {@link Responses}
     * @throws Exception any error; {@link Router} maps it to an HTTP error response
     */
    Map<String, Object> handle(Request request) throws Exception;
}
