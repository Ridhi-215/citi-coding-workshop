package com.example.common.validation;

import com.example.common.http.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a JSON request body field by field, collecting every error before failing.
 *
 * <pre>{@code
 * Validator v = new Validator(request.json());
 * String name = v.requiredString("name", 200);
 * LocalDate due = v.requiredDate("due_date");
 * v.throwIfInvalid();   // 400 VALIDATION_FAILED with {"name": "...", "due_date": "..."}
 * }</pre>
 *
 * <p>Getters return null for invalid values, so callers must call {@link #throwIfInvalid()}
 * before using the results.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, Object> body;
    private final Map<String, String> errors = new LinkedHashMap<>();

    /**
     * Creates a validator for a parsed JSON object.
     *
     * @param body request body
     */
    public Validator(Map<String, Object> body) {
        this.body = body == null ? Map.of() : body;
    }

    /**
     * Reads a required, non-blank string.
     *
     * @param field     field name
     * @param maxLength longest allowed length
     * @return trimmed value, or null if invalid
     */
    public String requiredString(String field, int maxLength) {
        String value = optionalString(field, maxLength);
        if (value == null && !errors.containsKey(field)) {
            errors.put(field, "is required");
        }
        return value;
    }

    /**
     * Reads an optional string; blank becomes null.
     *
     * @param field     field name
     * @param maxLength longest allowed length
     * @return trimmed value or null
     */
    public String optionalString(String field, int maxLength) {
        Object raw = body.get(field);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String s)) {
            errors.put(field, "must be text");
            return null;
        }
        String value = s.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            errors.put(field, "must be at most " + maxLength + " characters");
            return null;
        }
        return value;
    }

    /**
     * Reads a required email address, lower-cased.
     *
     * @param field field name
     * @return normalized email, or null if invalid
     */
    public String requiredEmail(String field) {
        String value = requiredString(field, 254);
        if (value != null && !EMAIL.matcher(value).matches()) {
            errors.put(field, "must be a valid email address");
            return null;
        }
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Reads an optional value that must be one of a fixed set.
     *
     * @param field    field name
     * @param allowed  allowed values
     * @param fallback value when missing
     * @return value, fallback when missing, or null if invalid
     */
    public String optionalEnum(String field, Set<String> allowed, String fallback) {
        String value = optionalString(field, 50);
        if (value == null) {
            return errors.containsKey(field) ? null : fallback;
        }
        if (!allowed.contains(value)) {
            errors.put(field, "must be one of " + String.join(", ", allowed.stream().sorted().toList()));
            return null;
        }
        return value;
    }

    /**
     * Reads a required value that must be one of a fixed set.
     *
     * @param field   field name
     * @param allowed allowed values
     * @return value, or null if missing or invalid
     */
    public String requiredEnum(String field, Set<String> allowed) {
        String value = optionalEnum(field, allowed, null);
        if (value == null && !errors.containsKey(field)) {
            errors.put(field, "is required");
        }
        return value;
    }

    /**
     * Reads a required ISO date ({@code yyyy-MM-dd}).
     *
     * @param field field name
     * @return date, or null if missing or invalid
     */
    public LocalDate requiredDate(String field) {
        LocalDate value = optionalDate(field);
        if (value == null && !errors.containsKey(field)) {
            errors.put(field, "is required");
        }
        return value;
    }

    /**
     * Reads an optional ISO date ({@code yyyy-MM-dd}).
     *
     * @param field field name
     * @return date or null
     */
    public LocalDate optionalDate(String field) {
        String value = optionalString(field, 10);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            errors.put(field, "must be a date in YYYY-MM-DD format");
            return null;
        }
    }

    /**
     * Reads an optional whole number within bounds.
     *
     * @param field field name
     * @param min   smallest allowed value
     * @param max   largest allowed value
     * @return value or null
     */
    public Integer optionalInt(String field, int min, int max) {
        Object raw = body.get(field);
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            return null;
        }
        Integer value = null;
        if (raw instanceof Integer i) {
            value = i;
        } else if (raw instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            value = l.intValue();
        } else if (raw instanceof String s) {
            try {
                value = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        if (value == null) {
            errors.put(field, "must be a whole number");
            return null;
        }
        if (value < min || value > max) {
            errors.put(field, "must be between " + min + " and " + max);
            return null;
        }
        return value;
    }

    /**
     * Reads a required whole number within bounds.
     *
     * @param field field name
     * @param min   smallest allowed value
     * @param max   largest allowed value
     * @return value, or null if missing or invalid
     */
    public Integer requiredInt(String field, int min, int max) {
        Integer value = optionalInt(field, min, max);
        if (value == null && !errors.containsKey(field)) {
            errors.put(field, "is required");
        }
        return value;
    }

    /**
     * Reads an optional id referencing another record.
     *
     * @param field field name
     * @return id or null
     */
    public Long optionalId(String field) {
        Integer value = optionalInt(field, 1, Integer.MAX_VALUE);
        return value == null ? null : value.longValue();
    }

    /**
     * Reads a required id referencing another record.
     *
     * @param field field name
     * @return id, or null if missing or invalid
     */
    public Long requiredId(String field) {
        Integer value = requiredInt(field, 1, Integer.MAX_VALUE);
        return value == null ? null : value.longValue();
    }

    /**
     * Reads a decimal amount (2 decimal places at most) within bounds.
     *
     * @param field    field name
     * @param min      smallest allowed value (inclusive)
     * @param required whether the field must be present
     * @return value, or null if missing or invalid
     */
    public BigDecimal money(String field, BigDecimal min, boolean required) {
        Object raw = body.get(field);
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            if (required) {
                errors.put(field, "is required");
            }
            return null;
        }
        BigDecimal value;
        try {
            value = raw instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            errors.put(field, "must be a number");
            return null;
        }
        if (value.scale() > 2) {
            errors.put(field, "must have at most 2 decimal places");
            return null;
        }
        if (value.compareTo(min) < 0) {
            errors.put(field, "must be at least " + min.toPlainString());
            return null;
        }
        if (value.compareTo(new BigDecimal("999999999999.99")) > 0) {
            errors.put(field, "is too large");
            return null;
        }
        return value;
    }

    /**
     * Records an error when a cross-field rule fails.
     *
     * @param ok      whether the rule holds
     * @param field   field to attach the error to
     * @param message error message
     */
    public void check(boolean ok, String field, String message) {
        if (!ok && !errors.containsKey(field)) {
            errors.put(field, message);
        }
    }

    /**
     * Adds an error directly (e.g. after a database lookup).
     *
     * @param field   field name
     * @param message error message
     */
    public void addError(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    /**
     * Returns whether a field already has an error.
     *
     * @param field field name
     * @return true if invalid
     */
    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    /**
     * Throws 400 with every collected error, if any.
     *
     * @throws ApiException 400 {@code VALIDATION_FAILED}
     */
    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw ApiException.validation(Map.copyOf(errors));
        }
    }
}
