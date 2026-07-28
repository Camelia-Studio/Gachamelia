package org.camelia.studio.gachamelia.api.dto;

import java.util.List;

public record ApiRole(long id, String name, int percentage, ApiEmoji emoji, List<ApiRoleStat> stats) {
}
