package com.codeflow.migration.model;

import java.util.List;

/** `.codeflow/golden/manifest.json` içeriği. */
public record GoldenManifest(int version, List<GoldenCase> cases) {
}
