package com.genosha.vitral.client.api;

import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.LibraryView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client decides which view a changed photo still belongs to the same way the server does. */
class LibraryViewTest {

    @Test
    void theTimelineLeavesOutArchivedAndTrashedPhotos() {
        assertTrue(LibraryView.TIMELINE.contains(photo(false, false, false)));
        assertTrue(LibraryView.TIMELINE.contains(photo(true, false, false)));
        assertFalse(LibraryView.TIMELINE.contains(photo(false, true, false)));
        assertFalse(LibraryView.TIMELINE.contains(photo(false, false, true)));
    }

    @Test
    void favoritesIncludeArchivedOnesButNotTheTrash() {
        assertTrue(LibraryView.FAVORITES.contains(photo(true, true, false)));
        assertFalse(LibraryView.FAVORITES.contains(photo(true, false, true)));
        assertFalse(LibraryView.FAVORITES.contains(photo(false, false, false)));
    }

    @Test
    void theArchiveAndTheTrashHoldTheirOwn() {
        assertTrue(LibraryView.ARCHIVE.contains(photo(false, true, false)));
        assertFalse(LibraryView.ARCHIVE.contains(photo(false, true, true)));
        assertTrue(LibraryView.TRASH.contains(photo(false, true, true)));
        assertFalse(LibraryView.TRASH.contains(photo(true, false, false)));
    }

    private static Asset photo(boolean favorite, boolean archived, boolean trashed) {
        return new Asset(UUID.randomUUID(), "PHOTO", "IMG.jpg", "image/jpeg", 1, 10, 10, Instant.EPOCH, null,
                Instant.EPOCH, Models.ThumbnailStatus.READY, null, null, favorite, archived,
                trashed ? Instant.EPOCH : null);
    }
}
