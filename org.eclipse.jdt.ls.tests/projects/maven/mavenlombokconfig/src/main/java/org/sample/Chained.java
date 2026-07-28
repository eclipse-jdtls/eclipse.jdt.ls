package org.sample;

import lombok.Data;

/**
 * The setters generated for this class only return {@code Chained} instead of
 * {@code void} when the {@code lombok.config} sitting in the project root is
 * picked up by Lombok.
 */
@Data
public class Chained {
    private String name;
}
