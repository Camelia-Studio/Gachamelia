package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnsureUserRequest(@JsonProperty("staff") Boolean isStaff) {
    public static EnsureUserRequest normal() {
        return new EnsureUserRequest(null);
    }

    public static EnsureUserRequest staff() {
        return new EnsureUserRequest(true);
    }
}
