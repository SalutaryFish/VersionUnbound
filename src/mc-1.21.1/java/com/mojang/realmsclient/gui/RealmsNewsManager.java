package com.mojang.realmsclient.gui;

import com.mojang.realmsclient.dto.RealmsNews;
import com.mojang.realmsclient.util.RealmsPersistence;

public class RealmsNewsManager {
    private final RealmsPersistence newsLocalStorage;
    private boolean hasUnreadNews;
    private String newsLink;

    public RealmsNewsManager(RealmsPersistence newsLocalStorage) {
        this.newsLocalStorage = newsLocalStorage;
        RealmsPersistence.RealmsPersistenceData realmsPersistenceData = newsLocalStorage.read();
        this.hasUnreadNews = realmsPersistenceData.hasUnreadNews;
        this.newsLink = realmsPersistenceData.newsLink;
    }

    public boolean hasUnreadNews() {
        return this.hasUnreadNews;
    }

    public String newsLink() {
        return this.newsLink;
    }

    public void updateUnreadNews(RealmsNews realmsNews) {
        RealmsPersistence.RealmsPersistenceData realmsPersistenceData = this.updateNewsStorage(realmsNews);
        this.hasUnreadNews = realmsPersistenceData.hasUnreadNews;
        this.newsLink = realmsPersistenceData.newsLink;
    }

    private RealmsPersistence.RealmsPersistenceData updateNewsStorage(RealmsNews realmsNews) {
        RealmsPersistence.RealmsPersistenceData realmsPersistenceData = new RealmsPersistence.RealmsPersistenceData();
        realmsPersistenceData.newsLink = realmsNews.newsLink;
        RealmsPersistence.RealmsPersistenceData realmsPersistenceData2 = this.newsLocalStorage.read();
        boolean bl = realmsPersistenceData.newsLink == null || realmsPersistenceData.newsLink.equals(realmsPersistenceData2.newsLink);
        if (bl) {
            return realmsPersistenceData2;
        }

        realmsPersistenceData.hasUnreadNews = true;
        this.newsLocalStorage.save(realmsPersistenceData);
        return realmsPersistenceData;
    }
}
