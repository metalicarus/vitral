package com.genosha.vitral.album;

import com.genosha.vitral.asset.Asset;
import com.genosha.vitral.common.ApiException;
import com.genosha.vitral.common.Timestamps;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates, renames and deletes albums, and puts photos in and out of them. */
@ApplicationScoped
public class AlbumService {

    private static final Logger LOG = Logger.getLogger(AlbumService.class);
    /** Largest number of photos added or removed in one request. */
    public static final int MAX_PHOTOS_PER_CHANGE = 500;
    private static final int MAX_NAME = 100;

    public List<AlbumView> list(UUID ownerId, UUID containing) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Set<UUID> withAsset = containing == null ? Set.of() : new HashSet<>(AlbumAsset.<AlbumAsset>list(
                    "key.assetId", containing).stream().map(entry -> entry.key.albumId).toList());
            return Album.<Album>list("ownerId", Sort.descending("updatedAt"), ownerId).stream()
                    .map(album -> view(album, containing == null ? null : withAsset.contains(album.id)))
                    .toList();
        });
    }

    public AlbumView get(UUID ownerId, UUID albumId) {
        return QuarkusTransaction.requiringNew().call(() -> view(owned(ownerId, albumId), null));
    }

    public AlbumView create(UUID ownerId, String name) {
        String cleaned = validName(name);
        AlbumView created = QuarkusTransaction.requiringNew().call(() -> {
            Album album = new Album();
            album.ownerId = ownerId;
            album.name = cleaned;
            album.createdAt = Timestamps.now();
            album.updatedAt = album.createdAt;
            album.persist();
            return view(album, null);
        });
        LOG.infof("Album \"%s\" (%s) created", cleaned, created.id());
        return created;
    }

    /**
     * Renames the album and/or sets its cover; null leaves that as it is.
     *
     * @param coverAssetId a photo in the album
     */
    public AlbumView change(UUID ownerId, UUID albumId, String name, UUID coverAssetId) {
        String cleaned = name == null ? null : validName(name);
        return QuarkusTransaction.requiringNew().call(() -> {
            Album album = owned(ownerId, albumId);
            if (cleaned != null && !cleaned.equals(album.name)) {
                LOG.infof("Album %s renamed from \"%s\" to \"%s\"", albumId, album.name, cleaned);
                album.name = cleaned;
                album.updatedAt = Timestamps.now();
            }
            if (coverAssetId != null) {
                if (AlbumAsset.findById(new AlbumAsset.Key(albumId, coverAssetId)) == null) {
                    throw ApiException.badRequest("cover_not_in_album", "The cover must be a photo in the album.");
                }
                album.coverAssetId = coverAssetId;
                LOG.infof("Album %s now has asset %s as its cover", albumId, coverAssetId);
            }
            return view(album, null);
        });
    }

    /** Deletes the album. Its photos stay in the library. */
    public void delete(UUID ownerId, UUID albumId) {
        String name = QuarkusTransaction.requiringNew().call(() -> {
            Album album = owned(ownerId, albumId);
            // The database removes the album's entries along with it (ON DELETE CASCADE).
            album.delete();
            return album.name;
        });
        LOG.infof("Album \"%s\" (%s) deleted; its photos stay in the library", name, albumId);
    }

    /** Adds the owner's photos to the album; photos already there, or not the owner's, are skipped. */
    public int add(UUID ownerId, UUID albumId, List<UUID> assetIds) {
        List<UUID> ids = checkedIds(assetIds);
        int added = QuarkusTransaction.requiringNew().call(() -> {
            Album album = owned(ownerId, albumId);
            Set<UUID> present = new HashSet<>(AlbumAsset.<AlbumAsset>list("key.albumId = ?1 and key.assetId in ?2",
                    albumId, ids).stream().map(entry -> entry.key.assetId).toList());
            List<UUID> mine = Asset.<Asset>list("ownerId = ?1 and id in ?2", ownerId, ids).stream()
                    .map(asset -> asset.id).toList();
            Instant now = Timestamps.now();
            int count = 0;
            for (UUID assetId : mine) {
                if (present.add(assetId)) {
                    AlbumAsset entry = new AlbumAsset();
                    entry.key = new AlbumAsset.Key(albumId, assetId);
                    entry.addedAt = now;
                    entry.persist();
                    count++;
                }
            }
            if (count > 0) {
                album.updatedAt = now;
            }
            return count;
        });
        LOG.infof("%d photo(s) added to album %s", added, albumId);
        return added;
    }

    /** Takes photos out of the album; they stay in the library. */
    public int remove(UUID ownerId, UUID albumId, List<UUID> assetIds) {
        List<UUID> ids = checkedIds(assetIds);
        int removed = QuarkusTransaction.requiringNew().call(() -> {
            Album album = owned(ownerId, albumId);
            int count = (int) AlbumAsset.delete("key.albumId = ?1 and key.assetId in ?2", albumId, ids);
            if (album.coverAssetId != null && ids.contains(album.coverAssetId)) {
                album.coverAssetId = null;
            }
            if (count > 0) {
                album.updatedAt = Timestamps.now();
            }
            return count;
        });
        LOG.infof("%d photo(s) removed from album %s", removed, albumId);
        return removed;
    }

    /** Checks the album belongs to the owner; answers 404 alike for someone else's and for none. */
    void requireOwned(UUID ownerId, UUID albumId) {
        QuarkusTransaction.requiringNew().run(() -> owned(ownerId, albumId));
    }

    private static Album owned(UUID ownerId, UUID albumId) {
        Album album = Album.<Album>find("id = ?1 and ownerId = ?2", albumId, ownerId).firstResult();
        if (album == null) {
            throw ApiException.notFound("album_not_found", "Album not found.");
        }
        return album;
    }

    /**
     * The album as the API shows it. The photo count and the cover leave out photos in the
     * trash; without a chosen cover (or when it is in the trash), the newest photo is used.
     */
    private static AlbumView view(Album album, Boolean contains) {
        String visible = "from Asset a where a.trashedAt is null and a.id in"
                + " (select aa.key.assetId from AlbumAsset aa where aa.key.albumId = ?1)";
        long photos = Asset.count(visible, album.id);
        UUID cover = null;
        if (album.coverAssetId != null) {
            Asset chosen = Asset.findById(album.coverAssetId);
            cover = chosen != null && chosen.trashedAt == null ? chosen.id : null;
        }
        if (cover == null && photos > 0) {
            Asset newest = Asset.<Asset>find(visible, Sort.descending("takenAt", "id"), album.id).firstResult();
            cover = newest == null ? null : newest.id;
        }
        return new AlbumView(album.id, album.name, cover, photos, album.createdAt, album.updatedAt, contains);
    }

    private static String validName(String name) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_NAME) {
            throw ApiException.badRequest("invalid_album_name", "The album name must have 1 to 100 characters.");
        }
        return cleaned;
    }

    private static List<UUID> checkedIds(List<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw ApiException.badRequest("missing_assets", "Send the photos in the field \"assetIds\".");
        }
        if (assetIds.size() > MAX_PHOTOS_PER_CHANGE) {
            throw ApiException.badRequest("too_many_assets",
                    "Send at most " + MAX_PHOTOS_PER_CHANGE + " photos per request.");
        }
        return assetIds.stream().distinct().toList();
    }
}
