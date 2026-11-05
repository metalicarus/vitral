package com.genosha.vitral.asset;

import java.util.List;

/** One page of the asset list. {@code nextCursor} is null on the last page. */
public record AssetPage(List<AssetView> items, String nextCursor) {
}