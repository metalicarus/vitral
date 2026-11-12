package com.genosha.vitral.asset;

import com.genosha.vitral.media.MediaInfo;
import com.genosha.vitral.media.MediaInspector;
import com.genosha.vitral.storage.OriginalFiles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the metadata of photos cataloged by an older version of the server again, so they
 * gain the fields added since (camera, exposure, GPS), without anyone sending them again.
 *
 * <p>Runs once, in the background, at every startup; when every row is current it finds
 * nothing to do. Only the capture fields change: dates, sizes and thumbnails stay as they are.
 */
@ApplicationScoped
public class MetadataRefresh {

    private static final Logger LOG = Logger.getLogger(MetadataRefresh.class);
    private static final int BATCH = 100;

    @Inject
    MediaInspector inspector;

    @Inject
    OriginalFiles originals;

    void start(@Observes StartupEvent event) {
        Thread.ofPlatform().name("vitral-metadata-refresh").daemon().start(() -> {
            try {
                refreshAll();
            } catch (RuntimeException e) {
                LOG.error("Reading the metadata of older photos again failed; it is tried again on the next start", e);
            }
        });
    }

    private record Target(UUID id, UUID folderId, String relativePath) {
    }

    /**
     * @return how many photos were read again
     */
    int refreshAll() {
        int refreshed = 0;
        int missing = 0;
        String after = "";
        while (true) {
            String cursor = after;
            List<Target> batch = QuarkusTransaction.requiringNew().call(() -> Asset.<Asset>find(
                            "metadataVersion < ?1 and str(id) > ?2", Sort.ascending("id"), Asset.METADATA_VERSION, cursor)
                    .page(0, BATCH).list().stream()
                    .map(asset -> new Target(asset.id, asset.folderId, asset.relativePath))
                    .toList());
            if (batch.isEmpty()) {
                break;
            }
            for (Target target : batch) {
                Optional<MediaInfo> media = read(target);
                if (media.isEmpty()) {
                    // The original is missing, for example deleted from a watched folder; tried again next start.
                    missing++;
                    continue;
                }
                QuarkusTransaction.requiringNew().run(() -> {
                    Asset asset = Asset.findById(target.id());
                    if (asset != null) {
                        asset.describeCapture(media.get());
                    }
                });
                refreshed++;
            }
            after = batch.getLast().id().toString();
        }
        if (refreshed > 0 || missing > 0) {
            LOG.infof("Read the metadata of %d older photo(s) again; %d original(s) could not be found", refreshed,
                    missing);
        }
        return refreshed;
    }

    private Optional<MediaInfo> read(Target target) {
        try {
            Path file = originals.resolve(target.folderId(), target.relativePath());
            return Files.isRegularFile(file) ? inspector.inspect(file) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
