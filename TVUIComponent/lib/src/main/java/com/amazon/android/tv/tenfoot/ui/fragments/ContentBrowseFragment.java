/**
 * This file was modified by Amazon:
 * Copyright 2015-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.amazon.android.tv.tenfoot.ui.fragments;

import com.amazon.android.contentbrowser.ContentBrowser;
import com.amazon.android.contentbrowser.ContentLoader;
import com.amazon.android.contentbrowser.helper.AuthHelper;
import com.amazon.android.model.Action;
import com.amazon.android.model.PlaylistAction;
import com.amazon.android.model.content.Content;
import com.amazon.android.model.content.ContentContainer;
import com.amazon.android.model.content.constants.ExtraKeys;
import com.amazon.android.recipe.Recipe;
import com.amazon.android.tv.tenfoot.R;
import com.amazon.android.tv.tenfoot.presenter.CardPresenter;
import com.amazon.android.tv.tenfoot.presenter.CustomListRowPresenter;
import com.amazon.android.tv.tenfoot.presenter.PosterCardPresenter;
import com.amazon.android.tv.tenfoot.presenter.SettingsCardPresenter;
import com.amazon.android.utils.Preferences;
import com.zype.fire.api.ZypeApi;
import com.zype.fire.api.ZypeSettings;
import com.zype.fire.auth.ZypeAuthentication;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v17.leanback.app.RowsFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * This fragment displays content in horizontal rows for browsing. Each row has its title displayed
 * above it.
 */
public class ContentBrowseFragment extends RowsFragment {

    private static final String TAG = ContentBrowseFragment.class.getSimpleName();
    private static final int WAIT_BEFORE_FOCUS_REQUEST_MS = 500;
    private OnBrowseRowListener mCallback;
    private ArrayObjectAdapter settingsAdapter = null;
    /* Zype, Evgeny Cherkasov */
    ArrayObjectAdapter mRowsAdapter = null;
    private BroadcastReceiver receiver;

    // Container Activity must implement this interface.
    public interface OnBrowseRowListener {

        void onItemSelected(Object item);
    }

    /* Zype, Evgeny Cherkasov */
    @Override
    public void onResume() {
        super.onResume();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, new IntentFilter("DataUpdated"));
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        EventBus.getDefault().register(this);
        // This makes sure that the container activity has implemented the callback interface.
        // If not, it throws an exception.
        try {
            mCallback = (OnBrowseRowListener) getActivity();
        }
        catch (ClassCastException e) {
            throw new ClassCastException(getActivity().toString() +
                                                 " must implement " +
                                                 "OnBrowseRowListener: " + e);
        }

        CustomListRowPresenter customListRowPresenter = new CustomListRowPresenter();
        customListRowPresenter.setHeaderPresenter(new RowHeaderPresenter());

        // Uncomment this code to remove shadow from the cards
        //customListRowPresenter.setShadowEnabled(false);

        /* Zype, Evgney Cherkasov */
//        ArrayObjectAdapter mRowsAdapter = new ArrayObjectAdapter(customListRowPresenter);
        mRowsAdapter = new ArrayObjectAdapter(customListRowPresenter);

        loadRootContentContainer(mRowsAdapter);
        addSettingsActionsToRowAdapter(mRowsAdapter);

        setAdapter(mRowsAdapter);

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());

        // Wait for WAIT_BEFORE_FOCUS_REQUEST_MS for the data to load before requesting focus.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (getView() != null) {
                VerticalGridView verticalGridView = findGridViewFromRoot(getView());
                if (verticalGridView != null) {
                    verticalGridView.requestFocus();
                }
            }
        }, WAIT_BEFORE_FOCUS_REQUEST_MS);

        /* Zype, Evgeny Cherkasov */
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateContents(mRowsAdapter);
            }
        };

    }

    /* Zype, Evgeny Cherkasov */
    @Override
    public void onPause() {
        super.onPause();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
        }
    }

    /**
     * Event bus listener method to listen for authentication updates from AUthHelper and update
     * the login action status in settings.
     *
     * @param authenticationStatusUpdateEvent Broadcast event for update in authentication status.
     */
    @Subscribe
    public void onAuthenticationStatusUpdateEvent(AuthHelper.AuthenticationStatusUpdateEvent
                                                          authenticationStatusUpdateEvent) {

        if (settingsAdapter != null) {
//            settingsAdapter.notifyArrayItemRangeChanged(0, settingsAdapter.size());
            settingsAdapter.clear();
            List<Action> settings = ContentBrowser.getInstance(getActivity()).getSettingsActions();
            if (settings != null && !settings.isEmpty()) {
                for (Action item : settings) {
                    settingsAdapter.add(item);
                }
            }
            else {
                Log.d(TAG, "No settings were found");
            }
//            settingsAdapter.notifyAll();
            settingsAdapter.notifyArrayItemRangeChanged(0, settingsAdapter.size());
        }
        /* Zype, Evgney Cherkasov */
        if (mRowsAdapter != null) {
            mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
        }
    }

    private void loadRootContentContainer(ArrayObjectAdapter rowsAdapter) {

        ContentContainer rootContentContainer = ContentBrowser.getInstance(getActivity())
                                                              .getRootContentContainer();

        CardPresenter cardPresenter = new CardPresenter();
        PosterCardPresenter posterCardPresenter = new PosterCardPresenter();

        for (ContentContainer contentContainer : rootContentContainer.getContentContainers()) {

            /* Zype, Evgeny Cherkasob */
            // Don't show My Library content container
            if (contentContainer.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG).equals(ZypeSettings.ROOT_MY_LIBRARY_PLAYLIST_ID)) {
                continue;
            }

            HeaderItem header = new HeaderItem(0, contentContainer.getName());
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
            /* Zype, Evgeny Cherkasov */
            if (contentContainer.getExtraStringValue(ContentContainer.EXTRA_THUMBNAIL_LAYOUT).equals("poster")) {
                listRowAdapter = new ArrayObjectAdapter(posterCardPresenter);
            }

            for (ContentContainer innerContentContainer : contentContainer.getContentContainers()) {
                listRowAdapter.add(innerContentContainer);
            }

            for (Content content : contentContainer.getContents()) {
                listRowAdapter.add(content);
            }

            /* Zype, Evgeny Cherkasov */
            // Update NextPage parameter because the first page of playlist videos was loaded
            // while running global recipes chain
            // TODO: Probably it would better to move updating NextPage to the getContentsObservable()
            if (contentContainer.getExtraValueAsInt(ExtraKeys.NEXT_PAGE) == 1) {
                if (contentContainer.getExtraValueAsInt(ContentContainer.EXTRA_PLAYLIST_ITEM_COUNT) > ZypeApi.PER_PAGE_DEFAULT) {
                    contentContainer.setExtraValue(ExtraKeys.NEXT_PAGE, 2);
                }
                else {
                    contentContainer.setExtraValue(ExtraKeys.NEXT_PAGE, -1);
                }
            }
            if (contentContainer.getExtraValueAsInt(ExtraKeys.NEXT_PAGE) > 0) {
                PlaylistAction action = new PlaylistAction();
                action.setAction(ContentBrowser.NEXT_PAGE)
                        .setIconResourceId(com.amazon.android.contentbrowser.R.drawable.ic_add_white_48dp)
                        .setLabel1(getString(R.string.action_load_more));
                action.setExtraValue(PlaylistAction.EXTRA_PLAYLIST_ID, contentContainer.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG));
                listRowAdapter.add(action);
            }

            rowsAdapter.add(new ListRow(header, listRowAdapter));
        }
    }

    private void addSettingsActionsToRowAdapter(ArrayObjectAdapter arrayObjectAdapter) {

        List<Action> settings = ContentBrowser.getInstance(getActivity()).getSettingsActions();

        if (settings != null && !settings.isEmpty()) {

            SettingsCardPresenter cardPresenter = new SettingsCardPresenter();
            settingsAdapter = new ArrayObjectAdapter(cardPresenter);

            for (Action item : settings) {
                settingsAdapter.add(item);
            }
        }
        else {
            Log.d(TAG, "No settings were found");
        }

        if (settingsAdapter != null) {
            // Create settings header and row
            HeaderItem header = new HeaderItem(0, getString(R.string.settings_title));
            arrayObjectAdapter.add(new ListRow(header, settingsAdapter));
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {

        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            /* Zype, Evgeny Cherkasov */
            // If selected playlist contains videos then open video details screen of the first video
            // in the playlist
            if (item instanceof ContentContainer) {
                ContentContainer contentContainer = (ContentContainer) item;
                if (!contentContainer.getContents().isEmpty()) {
                    item = contentContainer.getContents().get(0);
                }
                else {
                    if (Integer.valueOf(contentContainer.getExtraStringValue("playlistItemCount")) > 0) {
                        // Playlist has  videos, but they is not loaded yet.
                        // Load videos and then open video detail screen of the first video in the playlist
                        ContentLoader.ILoadContentForContentContainer listener = new ContentLoader.ILoadContentForContentContainer() {
                            @Override
                            public void onContentsLoaded() {
                                ContentBrowser.getInstance(getActivity())
                                        .setLastSelectedContent(contentContainer.getContents().get(0))
                                        .switchToScreen(ContentBrowser.CONTENT_DETAILS_SCREEN);
                            }
                        };
                        // TODO: Add mCompositeSubscription parameter from ContentBrowser
                        ContentLoader.getInstance(getActivity()).loadContentForContentContainer(contentContainer, getActivity(), listener);
                        return;
                    }
                }
            }

            if (item instanceof Content) {
                Content content = (Content) item;
                Log.d(TAG, "Content with title " + content.getTitle() + " was clicked");

                /* Zype, Evgeny Cherkasov */
                // Get video entitlement
                if (ZypeSettings.UNIVERSAL_TVOD) {
                    if (!content.getExtras().containsKey(Content.EXTRA_ENTITLED)) {
                        String accessToken = Preferences.getString(ZypeAuthentication.ACCESS_TOKEN);
                        HashMap<String, String> params = new HashMap<>();
                        params.put(ZypeApi.ACCESS_TOKEN, accessToken);
                        ZypeApi.getInstance().getApi().checkVideoEntitlement(content.getId(), params).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                                Log.e(TAG, "onItemClicked(): check video entitlement: code=" + response.code());
                                if (response.isSuccessful()) {
                                    content.setExtraValue(Content.EXTRA_ENTITLED, true);
                                }
                                else {
                                    content.setExtraValue(Content.EXTRA_ENTITLED, false);
                                }
                                ContentBrowser.getInstance(getActivity())
                                        .setLastSelectedContent(content)
                                        .switchToScreen(ContentBrowser.CONTENT_DETAILS_SCREEN);
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> call, Throwable t) {
                                Log.e(TAG, "onItemClicked(): check video entitlement: failed");
                                content.setExtraValue(Content.EXTRA_ENTITLED, false);
                                ContentBrowser.getInstance(getActivity())
                                        .setLastSelectedContent(content)
                                        .switchToScreen(ContentBrowser.CONTENT_DETAILS_SCREEN);
                            }
                        });
                    }
                    else {
                        ContentBrowser.getInstance(getActivity())
                                .setLastSelectedContent(content)
                                .switchToScreen(ContentBrowser.CONTENT_DETAILS_SCREEN);
                    }
                }
                else {
                    ContentBrowser.getInstance(getActivity())
                            .setLastSelectedContent(content)
                            .switchToScreen(ContentBrowser.CONTENT_DETAILS_SCREEN);
                }
            }
            else if (item instanceof ContentContainer) {
                ContentContainer contentContainer = (ContentContainer) item;
                Log.d(TAG, "ContentContainer with name " + contentContainer.getName() + " was " +
                        "clicked");

                ContentBrowser.getInstance(getActivity())
                              .setLastSelectedContentContainer(contentContainer)
                              .switchToScreen(ContentBrowser.CONTENT_SUBMENU_SCREEN);
            }
            /* Zype, Evgeny Cherkasov */
            else if (item instanceof PlaylistAction) {
                PlaylistAction action = (PlaylistAction) item;
                if (action.getAction().equals(ContentBrowser.NEXT_PAGE)) {
                    Log.d(TAG, "Next page button was clicked");
                    ContentBrowser.getInstance(getActivity()).loadPlaylistVideos(action.getExtraValueAsString(PlaylistAction.EXTRA_PLAYLIST_ID));
                }
            }
            else if (item instanceof Action) {
                Action settingsAction = (Action) item;
                Log.d(TAG, "Settings with title " + settingsAction.getAction() + " was clicked");
                ContentBrowser.getInstance(getActivity())
                              .settingsActionTriggered(getActivity(),
                                                       settingsAction);
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {

        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {

            mCallback.onItemSelected(item);
        }
    }

    /* Zype, Evgeny Cherkasov */
    private void updateContents(ArrayObjectAdapter rowsAdapter) {

        ContentContainer rootContentContainer = ContentBrowser.getInstance(getActivity())
                .getRootContentContainer();

        int index = 0;
        for (ContentContainer contentContainer : rootContentContainer.getContentContainers()) {
            // Skip 'My Library' content container
            if (contentContainer.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG).equals(ZypeSettings.ROOT_MY_LIBRARY_PLAYLIST_ID)) {
                continue;
            }

            ListRow row = (ListRow) rowsAdapter.get(index);
            ArrayObjectAdapter listRowAdapter = (ArrayObjectAdapter) row.getAdapter();

            // Remove 'Load more' action button
            if (listRowAdapter.size() > 0 && listRowAdapter.get(listRowAdapter.size() - 1) instanceof PlaylistAction) {
                listRowAdapter.remove(listRowAdapter.get(listRowAdapter.size() - 1));
            }
            // Add new contents
            for (int i = listRowAdapter.size() - contentContainer.getContentContainerCount(); i < contentContainer.getContentCount(); i++) {
                listRowAdapter.add(contentContainer.getContents().get(i));
            }
            // Add a button for loading next page of playlist videos
            if (contentContainer.getExtraValueAsInt(ExtraKeys.NEXT_PAGE) > 0) {
                PlaylistAction action = new PlaylistAction();
                action.setAction(ContentBrowser.NEXT_PAGE)
                        .setIconResourceId(com.amazon.android.contentbrowser.R.drawable.ic_add_white_48dp)
                        .setLabel1(getString(R.string.action_load_more));
                action.setExtraValue(PlaylistAction.EXTRA_PLAYLIST_ID, contentContainer.getExtraStringValue(Recipe.KEY_DATA_TYPE_TAG));
                listRowAdapter.add(action);
            }

            index++;
        }
    }

}
