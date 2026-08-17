package com.church.app.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds the current query string minus the paging parameters.
 *
 * <p>Every pagination link has to carry the search and filters forward. Without that,
 * clicking "page 2" drops whatever was being searched for and lands on page 2 of
 * something else entirely -- which looks like the results changed on their own.
 *
 * <p>Values are URL-encoded, so a Tamil anbiyam name or a name with a space survives the
 * round trip.
 */
final class QueryParams {

    private final List<String> parts = new ArrayList<>();

    private QueryParams() {
    }

    static QueryParams of() {
        return new QueryParams();
    }

    QueryParams add(String name, Object value) {
        if (value == null || value.toString().isBlank()) {
            return this;
        }
        parts.add(name + "=" + URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        return this;
    }

    @Override
    public String toString() {
        return String.join("&", parts);
    }
}
