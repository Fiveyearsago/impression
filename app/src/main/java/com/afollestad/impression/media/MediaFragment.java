package com.afollestad.impression.media;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.impression.App;
import com.afollestad.impression.R;
import com.afollestad.impression.accounts.base.Account;
import com.afollestad.impression.api.FolderEntry;
import com.afollestad.impression.api.MediaEntry;
import com.afollestad.impression.cab.MediaCab;
import com.afollestad.impression.providers.SortMemoryProvider;
import com.afollestad.impression.utils.PrefUtils;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.widget.breadcrumbs.Crumb;

import java.io.File;
import java.util.List;

import rx.Single;
import rx.SingleSubscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;

import static android.app.Activity.RESULT_OK;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MediaFragment extends Fragment implements MediaView {

    protected
    @MediaAdapter.SortMode
    int sortCache;
    protected Crumb crumb;
    private RecyclerView mRecyclerView;
    private MediaAdapter mAdapter;
    private MediaPresenter mPresenter;
    private boolean mSortRememberDir = false;

    RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    final void jumpToTop(boolean animateChange) {
        if (animateChange) {
            //stopAnimation();
            mRecyclerView.smoothScrollToPosition(0);
        } else {
            mRecyclerView.scrollToPosition(0);
        }
    }

    final void setListShown(boolean shown) {
        View v = getView();
        if (v == null || getActivity() == null) return;
        if (shown) {
            v.findViewById(R.id.list).setVisibility(mAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.empty).setVisibility(mAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.progress).setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();
        } else {
            v.findViewById(R.id.list).setVisibility(View.GONE);
            v.findViewById(R.id.empty).setVisibility(View.GONE);
            v.findViewById(R.id.progress).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mPresenter.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((TextView) view.findViewById(R.id.empty)).setText(mPresenter.getEmptyText());
        mRecyclerView = (RecyclerView) view.findViewById(R.id.list);
        mRecyclerView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        mPresenter.onViewCreated();
    }

    @Override
    public void initializeRecyclerView(boolean gridMode, int size, MediaAdapter adapter) {
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContextCompat(), size));

        mAdapter = adapter;
        mRecyclerView.setAdapter(mAdapter);

        if (gridMode) {
            mPresenter.setGridModeOn(true);
        }
    }

    public void updateGridModeOn(boolean gridMode) {
        int gridSpacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        if (gridMode) {
            mRecyclerView.setPadding(gridSpacing, 0, 0, gridSpacing);
        } else {
            mRecyclerView.setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public void updateGridColumns(int size) {
        final GridLayoutManager layoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        layoutManager.setSpanCount(size);
    }

    @Override
    public MediaAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public Context getContextCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext();
        } else {
            return getActivity();
        }
    }

    public void saveScrollPosition() {
        if (crumb == null)
            return;
        crumb.setScrollPosition(((GridLayoutManager) mRecyclerView.getLayoutManager()).findFirstVisibleItemPosition());
        final View firstChild = mRecyclerView.getChildAt(0);
        if (firstChild != null)
            crumb.setScrollOffset((int) firstChild.getY());
    }

    private void restoreScrollPosition() {
        if (crumb == null)
            return;
        final int scrollY = crumb.getScrollPosition();
        if (scrollY > -1 && scrollY < getAdapter().getItemCount()) {
            ((GridLayoutManager) mRecyclerView.getLayoutManager()).scrollToPositionWithOffset(scrollY, crumb.getScrollOffset());
        }
    }

    protected final void setFilterMode(@MediaAdapter.FileFilterMode int mode) {
        PrefUtils.setFilterMode(getActivity(), mode);
        reload();
        getActivity().invalidateOptionsMenu();
    }

    protected final void setSortMode(@MediaAdapter.SortMode int mode, String rememberPath) {
        sortCache = mode;
        SortMemoryProvider.save(getActivity(), rememberPath, mode);
        mAdapter.setSortMode(mode);
        reload();
        getActivity().invalidateOptionsMenu();
    }


    private Single<List<? extends MediaEntry>> getAllEntries() {
        /*if (getActivity() == null) return Observable.empty().toSingle();*/
        return App.getCurrentAccount(getActivity())
                .flatMap(new Func1<Account, Single<List<? extends MediaEntry>>>() {
                    @Override
                    public Single<List<? extends MediaEntry>> call(Account account) {
                        if (!isAdded()) return null;
                        if (account != null) {
                            /*acc.getEntries(mPresenter.getPath(), PrefUtils.getOverviewMode(getActivity()),
                            PrefUtils.isExplorerMode(getActivity()), PrefUtils.getFilterMode(getActivity()),
                            SortMemoryProvider.getSortMode(getActivity(), mPresenter.getPath()), callback);*/
                            return account.getEntries(mPresenter.getPath(),
                                    PrefUtils.isExplorerMode(getActivity()),
                                    PrefUtils.getFilterMode(getActivity()),
                                    SortMemoryProvider.getSortMode(getActivity(),
                                            mPresenter.getPath()));
                        }
                        return null;
                    }
                });
    }

    private void invalidateEmptyText() {
        @MediaAdapter.FileFilterMode int mode = PrefUtils.getFilterMode(getActivity());
        View v = getView();
        if (v != null) {
            TextView empty = (TextView) v.findViewById(R.id.empty);
            if (empty != null) {
                switch (mode) {
                    default:
                        empty.setText(R.string.no_photosorvideos);
                        break;
                    case MediaAdapter.FILTER_PHOTOS:
                        empty.setText(R.string.no_photos);
                        break;
                    case MediaAdapter.FILTER_VIDEOS:
                        empty.setText(R.string.no_videos);
                        break;
                }
            }
        }
    }

    public final void reload() {
        final Activity act = getActivity();
        if (act == null || ContextCompat.checkSelfPermission(act, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }

        saveScrollPosition();
        invalidateEmptyText();
        setListShown(false);

        if (getAdapter() != null)
            getAdapter().clear();
        getAllEntries()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<List<? extends MediaEntry>>() {
                    @Override
                    public void onSuccess(List<? extends MediaEntry> entries) {
                        MediaEntry[] allEntries = entries.toArray(new MediaEntry[entries.size()]);
                        if (!isAdded())
                            return;
                        else if (getAdapter() != null)
                            getAdapter().addAll(allEntries);
                        setListShown(true);
                        restoreScrollPosition();
                        invalidateSubtitle(allEntries);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (getActivity() == null) return;
                        Utils.showErrorDialog(getActivity(), error);
                        error.printStackTrace();
                    }
                });
    }

    private void invalidateSubtitle(MediaEntry[] entries) {
        AppCompatActivity act = (AppCompatActivity) getActivity();
        if (act != null) {
            final boolean toolbarStats = PreferenceManager.getDefaultSharedPreferences(act)
                    .getBoolean("toolbar_album_stats", true);
            if (toolbarStats) {
                if (entries == null || entries.length == 0) {
                    act.getSupportActionBar().setSubtitle(getString(R.string.empty));
                    return;
                }

                int folderCount = 0;
                int albumCount = 0;
                int videoCount = 0;
                int photoCount = 0;
                for (MediaEntry e : entries) {
                    if (e.isFolder()) folderCount++;
                        //else if (e.isAlbum()) albumCount++;
                    else if (e.isVideo()) videoCount++;
                    else photoCount++;
                }
                final StringBuilder sb = new StringBuilder();
                if (albumCount > 1) {
                    sb.append(getString(R.string.x_albums, albumCount));
                } else if (albumCount == 1) {
                    sb.append(getString(R.string.one_album));
                }
                if (folderCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_folders, folderCount));
                } else if (folderCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_folder));
                }
                if (photoCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_photos, photoCount));
                } else if (photoCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_photo));
                }
                if (videoCount > 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.x_videos, videoCount));
                } else if (videoCount == 1) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(getString(R.string.one_video));
                }
                act.getSupportActionBar().setSubtitle(sb.toString());
            } else {
                act.getSupportActionBar().setSubtitle(null);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getAdapter() != null)
            getAdapter().changeContent(null, null, true, false);
    }

    public MediaPresenter getPresenter() {
        return mPresenter;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mPresenter = createPresenter();
        mPresenter.attachView(this);
        mPresenter.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.onResume();
    }

    public void setCrumb(Crumb crumb) {
        this.crumb = crumb;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK &&
                (requestCode == MediaCab.COPY_REQUEST_CODE || requestCode == MediaCab.MOVE_REQUEST_CODE)) {
            ((MainActivity) getActivity()).getMediaCab().finishCopyMove(new File(data.getData().getPath()), requestCode);
        }
    }

    private void setStatus(String status) {
        ((MainActivity) getActivity()).setStatus(status);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment, menu);

        if (getActivity() != null) {
            boolean isMain = mPresenter.getPath() == null || mPresenter.getPath().equals(FolderEntry.OVERVIEW_PATH);
            boolean isAlbumSelect = ((MainActivity) getActivity()).isSelectAlbumMode();
            menu.findItem(R.id.choose).setVisible(!isMain && isAlbumSelect);
            menu.findItem(R.id.viewMode).setVisible(!isAlbumSelect);
            menu.findItem(R.id.filter).setVisible(!isAlbumSelect);

            sortCache = SortMemoryProvider.getSortMode(getActivity(), mPresenter.getPath());
            switch (sortCache) {
                default:
                    menu.findItem(R.id.sortNameAsc).setChecked(true);
                    break;
                case MediaAdapter.SORT_NAME_DESC:
                    menu.findItem(R.id.sortNameDesc).setChecked(true);
                    break;
                case MediaAdapter.SORT_MODIFIED_DATE_ASC:
                    menu.findItem(R.id.sortModifiedAsc).setChecked(true);
                    break;
                case MediaAdapter.SORT_MODIFIED_DATE_DESC:
                    menu.findItem(R.id.sortModifiedDesc).setChecked(true);
                    break;
            }
            menu.findItem(R.id.sortCurrentDir).setChecked(mSortRememberDir);

            @MediaAdapter.FileFilterMode int filterMode = PrefUtils.getFilterMode(getActivity());
            switch (filterMode) {
                default:
                    setStatus(null);
                    menu.findItem(R.id.filterAll).setChecked(true);
                    break;
                case MediaAdapter.FILTER_PHOTOS:
                    setStatus(getString(R.string.filtering_photos));
                    menu.findItem(R.id.filterPhotos).setChecked(true);
                    break;
                case MediaAdapter.FILTER_VIDEOS:
                    setStatus(getString(R.string.filtering_videos));
                    menu.findItem(R.id.filterVideos).setChecked(true);
                    break;
            }

            switch (PrefUtils.getGridColumns(getActivity())) {
                default:
                    menu.findItem(R.id.gridSizeOne).setChecked(true);
                    break;
                case 2:
                    menu.findItem(R.id.gridSizeTwo).setChecked(true);
                    break;
                case 3:
                    menu.findItem(R.id.gridSizeThree).setChecked(true);
                    break;
                case 4:
                    menu.findItem(R.id.gridSizeFour).setChecked(true);
                    break;
                case 5:
                    menu.findItem(R.id.gridSizeFive).setChecked(true);
                    break;
                case 6:
                    menu.findItem(R.id.gridSizeSix).setChecked(true);
                    break;
            }
        }

        menu.findItem(R.id.viewExplorer).setChecked(PrefUtils.isExplorerMode(getActivity()));
        if (PrefUtils.isGridMode(getContextCompat())) {
            menu.findItem(R.id.viewMode).setIcon(R.drawable.ic_action_view_list);
        } else {
            menu.findItem(R.id.viewMode).setIcon(R.drawable.ic_action_view_grid);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.choose:
                getActivity().setResult(RESULT_OK, new Intent().setData(Uri.fromFile(new File(mPresenter.getPath()))));
                getActivity().finish();
                return true;
            case R.id.viewMode:
                mPresenter.setGridModeOn(!PrefUtils.isGridMode(getActivity()));
                return true;
            case R.id.viewExplorer:
                mPresenter.onOptionsItemSelected(item.getItemId());
                return true;
            case R.id.filterAll:
                setFilterMode(MediaAdapter.FILTER_ALL);
                return true;
            case R.id.filterPhotos:
                setFilterMode(MediaAdapter.FILTER_PHOTOS);
                return true;
            case R.id.filterVideos:
                setFilterMode(MediaAdapter.FILTER_VIDEOS);
                return true;
            case R.id.sortNameAsc:
                setSortMode(MediaAdapter.SORT_NAME_ASC, mPresenter.getPath());
                return true;
            case R.id.sortNameDesc:
                setSortMode(MediaAdapter.SORT_NAME_DESC, mSortRememberDir ? mPresenter.getPath() : null);
                return true;
            case R.id.sortModifiedAsc:
                setSortMode(MediaAdapter.SORT_MODIFIED_DATE_ASC, mSortRememberDir ? mPresenter.getPath() : null);
                return true;
            case R.id.sortModifiedDesc:
                setSortMode(MediaAdapter.SORT_MODIFIED_DATE_DESC, mSortRememberDir ? mPresenter.getPath() : null);
                return true;
            case R.id.sortCurrentDir:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    mSortRememberDir = true;
                    setSortMode(sortCache, mPresenter.getPath());
                } else {
                    mSortRememberDir = false;
                    SortMemoryProvider.forget(getActivity(), mPresenter.getPath());
                    setSortMode(SortMemoryProvider.getSortMode(getActivity(), null), null);
                }
                return true;
            case R.id.gridSizeOne:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(1);
                break;
            case R.id.gridSizeTwo:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(2);
                break;
            case R.id.gridSizeThree:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(3);
                break;
            case R.id.gridSizeFour:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(4);
                break;
            case R.id.gridSizeFive:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(5);
                break;
            case R.id.gridSizeSix:
                item.setChecked(!item.isChecked());
                mPresenter.setGridColumns(6);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    protected MediaPresenter createPresenter() {
        return new MediaPresenter();
    }
}