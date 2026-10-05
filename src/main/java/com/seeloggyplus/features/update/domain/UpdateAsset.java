package com.seeloggyplus.features.update.domain;

/** A downloadable update artifact described by the manifest. */
public record UpdateAsset(String url, long size, String sha256) {
}
