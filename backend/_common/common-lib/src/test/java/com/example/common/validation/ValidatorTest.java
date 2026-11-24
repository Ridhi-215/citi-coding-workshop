package com.example.common.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.common.http.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ValidatorTest {

    private static Validator v(Object... kv) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            body.put((String) kv[i], kv[i + 1]);
        }
        return new Validator(body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> errors(Validator v) {
        ApiException e = assertThrows(ApiException.class, v::throwIfInvalid);
        assertEquals(400, e.status());
        assertEquals("VALIDATION_FAILED", e.code());
        return (Map<String, String>) e.details();
    }

    @Test
    void stringsAreTrimmedAndLengthChecked() {
        Validator v = v("a", "  hi  ", "b", "   ", "c", 5, "d", "toolong");
        assertEquals("hi", v.requiredString("a", 10));
        assertNull(v.requiredString("b", 10));
        assertNull(v.optionalString("c", 10));
        assertNull(v.optionalString("d", 3));
        assertNull(v.optionalString("missing", 3));
        Map<String, String> e = errors(v);
        assertEquals("is required", e.get("b"));
        assertEquals("must be text", e.get("c"));
        assertTrue(e.get("d").contains("3"));
        assertFalse(e.containsKey("missing"));
    }

    @Test
    void emailIsLowerCasedAndChecked() {
        Validator v = v("ok", "Ann@Acme.COM", "bad", "ann@");
        assertEquals("ann@acme.com", v.requiredEmail("ok"));
        assertNull(v.requiredEmail("bad"));
        assertTrue(errors(v).get("bad").contains("email"));
    }

    @Test
    void enumsUseFallbackAndRejectUnknown() {
        Set<String> allowed = Set.of("a", "b");
        Validator v = v("x", "b", "y", "z");
        assertEquals("b", v.optionalEnum("x", allowed, "a"));
        assertEquals("a", v.optionalEnum("none", allowed, "a"));
        assertNull(v.optionalEnum("y", allowed, "a"));
        assertNull(v.requiredEnum("none2", allowed));
        Map<String, String> e = errors(v);
        assertEquals("must be one of a, b", e.get("y"));
        assertEquals("is required", e.get("none2"));
    }

    @Test
    void datesMustBeIso() {
        Validator v = v("d", "2026-02-28", "bad", "28/02/2026");
        assertEquals(LocalDate.of(2026, 2, 28), v.requiredDate("d"));
        assertNull(v.requiredDate("bad"));
        assertNull(v.requiredDate("missing"));
        assertNull(v.optionalDate("missing2"));
        Map<String, String> e = errors(v);
        assertTrue(e.get("bad").contains("YYYY-MM-DD"));
        assertEquals("is required", e.get("missing"));
        assertFalse(e.containsKey("missing2"));
    }

    @Test
    void integersAndIds() {
        Validator v = v("i", 5, "l", 7L, "s", " 9 ", "f", 1.5, "big", 500, "neg", 0, "text", "x");
        assertEquals(5, v.requiredInt("i", 1, 10));
        assertEquals(7L, v.requiredId("l"));
        assertEquals(9L, v.optionalId("s"));
        assertNull(v.optionalInt("f", 1, 10));
        assertNull(v.requiredInt("big", 1, 100));
        assertNull(v.optionalId("neg"));
        assertNull(v.optionalInt("text", 1, 10));
        assertNull(v.requiredId("missing"));
        assertNull(v.optionalId("missing2"));
        Map<String, String> e = errors(v);
        assertEquals("must be a whole number", e.get("f"));
        assertEquals("must be between 1 and 100", e.get("big"));
        assertTrue(e.containsKey("neg"));
        assertEquals("is required", e.get("missing"));
    }

    @Test
    void moneyChecksScaleRangeAndFormat() {
        Validator v = v("a", "10.50", "b", 3, "c", "1.234", "d", "-1", "e", "abc", "f", "1e15", "g", " ");
        assertEquals(new BigDecimal("10.50"), v.money("a", BigDecimal.ZERO, true));
        assertEquals(new BigDecimal("3"), v.money("b", BigDecimal.ZERO, true));
        assertNull(v.money("c", BigDecimal.ZERO, true));
        assertNull(v.money("d", BigDecimal.ZERO, true));
        assertNull(v.money("e", BigDecimal.ZERO, true));
        assertNull(v.money("f", BigDecimal.ZERO, true));
        assertNull(v.money("g", BigDecimal.ZERO, false));
        assertNull(v.money("h", BigDecimal.ZERO, true));
        Map<String, String> e = errors(v);
        assertTrue(e.get("c").contains("2 decimal"));
        assertTrue(e.get("d").contains("at least 0"));
        assertEquals("must be a number", e.get("e"));
        assertEquals("is too large", e.get("f"));
        assertFalse(e.containsKey("g"));
        assertEquals("is required", e.get("h"));
    }

    @Test
    void checkAndAddErrorKeepTheFirstMessage() {
        Validator v = v();
        v.check(true, "a", "never");
        v.check(false, "a", "first");
        v.addError("a", "second");
        assertTrue(v.hasError("a"));
        assertFalse(v.hasError("b"));
        assertEquals("first", errors(v).get("a"));
    }

    @Test
    void validInputDoesNotThrow() {
        assertDoesNotThrow(() -> v("a", "x").throwIfInvalid());
    }
}
