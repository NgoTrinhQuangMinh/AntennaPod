package de.danoeh.antennapod.net.download.service.episode.autodownload;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterfaceStub;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that the APFavoriteCleanupAlgorithm is working correctly.
 */
@RunWith(RobolectricTestRunner.class)
public class ExceptFavoriteCleanupAlgorithmTest extends DbCleanupTests {
    private final int numberOfItems = EPISODE_CACHE_SIZE * 2;

    public ExceptFavoriteCleanupAlgorithmTest() {
        setCleanupAlgorithm(UserPreferences.EPISODE_CLEANUP_EXCEPT_FAVORITE);
        AutoDownloadManager.setInstance(new AutoDownloadManagerImpl());
    }

    @Test
    public void testPerformAutoCleanupHandleUnplayed() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, false);

        AutoDownloadManager.getInstance().performAutoCleanup(context);
        for (int i = 0; i < files.size(); i++) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue("Only enough items should be deleted", files.get(i).exists());
            } else {
                assertFalse("Expected episode to be deleted", files.get(i).exists());
            }
        }
    }

    @Test
    public void testPerformAutoCleanupDeletesQueued() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, true, false);

        AutoDownloadManager.getInstance().performAutoCleanup(context);
        for (int i = 0; i < files.size(); i++) {
            if (i < EPISODE_CACHE_SIZE) {
                assertTrue("Only enough items should be deleted", files.get(i).exists());
            } else {
                assertFalse("Queued episodes should be deleted", files.get(i).exists());
            }
        }
    }

    @Test
    public void testPerformAutoCleanupSavesFavorited() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, true);

        AutoDownloadManager.getInstance().performAutoCleanup(context);
        for (int i = 0; i < files.size(); i++) {
            assertTrue("Favorite episodes should should not be deleted", files.get(i).exists());
        }
    }

    @Override
    public void testPerformAutoCleanupShouldNotDeleteBecauseInQueue() throws IOException {
        // Yes it should
    }

    @Override
    public void testPerformAutoCleanupShouldNotDeleteBecauseInQueue_withFeedsWithNoMedia() throws IOException {
        // Yes it should
    }

    @Test
    public void testPerformAutoCleanupFavoritesDoNotCountTowardsLimit() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        populateItems(numberOfItems, feed, items, files, FeedItem.UNPLAYED, false, false);

        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setFavorites(items.subList(EPISODE_CACHE_SIZE, items.size()));
        adapter.close();

        AutoDownloadManager.getInstance().performAutoCleanup(context);
        for (int i = 0; i < files.size(); i++) {
            assertTrue("Favorites should not count towards the episode limit", files.get(i).exists());
        }
    }

    @Test
    public void testAutoDownloadFavoritesDoNotCountTowardsLimit() throws IOException {
        Feed feed = new Feed("url", null, "title");
        List<FeedItem> items = new ArrayList<>();
        feed.setItems(items);
        List<File> files = new ArrayList<>();
        populateItems(EPISODE_CACHE_SIZE, feed, items, files, FeedItem.UNPLAYED, false, true);

        FeedItem newItem = new FeedItem(0, "new", "id-new", "link", new Date(), FeedItem.UNPLAYED, feed);
        newItem.setMedia(new FeedMedia(newItem, "url-new", 1, "audio/mpeg"));
        items.add(newItem);
        PodDBAdapter adapter = PodDBAdapter.getInstance();
        adapter.open();
        adapter.setCompleteFeed(feed);
        adapter.setQueue(Collections.singletonList(newItem));
        adapter.close();

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(UserPreferences.PREF_AUTODL_QUEUE, true)
                .putStringSet(UserPreferences.PREF_MOBILE_UPDATE, Collections.singleton("auto_download"))
                .commit();
        NetworkUtils.init(context);
        context.sendStickyBroadcast(new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING));

        List<FeedItem> downloaded = new ArrayList<>();
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceStub() {
            @Override
            public void download(Context context, FeedItem item) {
                downloaded.add(item);
            }
        });

        new AutomaticDownloadAlgorithm().autoDownloadUndownloadedItems(context).run();
        assertEquals("Favorites should not block automatic download", 1, downloaded.size());
        assertEquals(newItem.getId(), downloaded.get(0).getId());
    }
}
