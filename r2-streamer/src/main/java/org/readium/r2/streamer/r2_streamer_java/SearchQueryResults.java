package org.readium.r2.streamer.r2_streamer_java;

import java.util.ArrayList;
import java.util.List;

public class SearchQueryResults {
    private int searchCount;
    public List<SearchResult> searchResultList;

    public SearchQueryResults() {
        this.searchResultList = new ArrayList<>();
    }

    public SearchQueryResults(int searchCount, List<SearchResult> searchResultList) {
        this.searchCount = searchCount;
        this.searchResultList = searchResultList;
    }

    public int getSearchCount() {
        searchCount = searchResultList.size();
        return searchCount;
    }

    public List<SearchResult> getSearchResultList() {
        return searchResultList;
    }

    public void setSearchResultList(List<SearchResult> searchResultList) {
        this.searchResultList = searchResultList;
    }
}
