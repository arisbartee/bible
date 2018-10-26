package me.piebridge.bible.activity;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.AppBarLayout;

import me.piebridge.bible.Bible;
import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.ReadingAdapter;
import me.piebridge.bible.bridge.ReadingBridge;
import me.piebridge.bible.bridge.ReadingHandler;
import me.piebridge.bible.fragment.AddNotesFragment;
import me.piebridge.bible.fragment.FontsizeFragment;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.fragment.ShowAnnotationFragment;
import me.piebridge.bible.fragment.ShowNotesFragment;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ChooserUtils;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;
import me.piebridge.bible.utils.ThemeUtils;

/**
 * Created by thom on 15/10/18.
 */
public abstract class AbstractReadingActivity extends DrawerActivity
        implements ReadingBridge.Bridge, View.OnClickListener, ViewPager.OnPageChangeListener,
        AppBarLayout.OnOffsetChangedListener {

    private ActionMode actionMode;

    public static final String CSS = "css";
    public static final String OSIS = "osis";
    public static final String ID = "id";
    public static final String CURR = "curr";
    public static final String NEXT = "next";
    public static final String PREV = "prev";
    public static final String HUMAN = "human";
    public static final String CONTENT = "content";
    public static final String POSITION = "position";
    public static final String NOTES = "notes";
    public static final String VERSE = "verse";
    public static final String VERSE_START = "verseStart";
    public static final String VERSE_END = "verseEnd";
    public static final String FONT_SIZE = "fontsize";
    public static final String FONT_PATH = "fontPath";
    public static final String CROSS = "cross";
    public static final String SHANGTI = "shangti";
    public static final String VERSION = "version";
    public static final String SEARCH = "search";
    public static final String HIGHLIGHTED = "highlighted";
    public static final String RED = "red";

    public static final String COLOR_TEXT = "colorText";
    public static final String COLOR_LINK = "colorLink";
    public static final String COLOR_RED = "colorRed";

    public static final String COLOR_BACKGROUND = "colorBackground";
    public static final String COLOR_BACKGROUND_HIGHLIGHT = "backgroundHighlight";
    public static final String COLOR_BACKGROUND_SELECTION = "backgroundSelection";
    public static final String COLOR_BACKGROUND_HIGHLIGHT_SELECTION = "backgroundHighlightSelection";

    protected static final int POSITION_UNKNOWN = -1;

    private static final int REQUEST_CODE_SELECT = 1001;
    private static final int REQUEST_CODE_VERSION = 1002;
    private static final int FONT_SIZE_DEFAULT = FontsizeFragment.FONTSIZE_DEFAULT;

    private static final String FRAGMENT_ADD_NOTES = "add-notes";

    private static final String FRAGMENT_SHOW_NOTES = "show-notes";

    private static final String FRAGMENT_SHOW_ANNOTATIONS = "show-annotations";

    protected ViewPager mPager;
    private AppBarLayout mAppBar;

    protected ReadingAdapter mAdapter;

    private View mHeader;

    private Handler handler = new ReadingHandler(this);

    private String mTextColorNormal;
    private String mTextColorLink;
    private String mTextColorRed;

    private String mBackground;
    private String mBackgroundHighlight;
    private String mBackgroundSelection;
    private String mBackgroundHighlightSelection;

    private String fontPath;

    protected Bible bible;
    private TextView versionView;

    private boolean mDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mDark = ThemeUtils.isDark(this);
        setTheme();
        resolveColors();
        super.onCreate(savedInstanceState);
        setContentView(getContentLayout());

        mHeader = findHeader();
        mPager = findViewById(R.id.pager);
        bible = Bible.getInstance(getApplicationContext());
        fontPath = BibleUtils.getFontPath(this);
        mAdapter = new ReadingAdapter(getSupportFragmentManager(), retrieveOsisCount());
        initialize();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        fontPath = BibleUtils.getFontPath(this);
        String oldFontPath = mAdapter.getData(getCurrentPosition()).getString(FONT_PATH);
        if (!ObjectUtils.equals(fontPath, oldFontPath)) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ThemeUtils.isDark(this) != mDark) {
            recreate();
        } else {
            setCheckedItem(R.id.menu_reading);
        }
    }

    private void resolveColors() {
        mTextColorNormal = ColorUtils.rgba(ColorUtils.resolve(this, R.attr.textColorNormal));
        mTextColorLink = ColorUtils.rgba(ColorUtils.resolve(this, android.R.attr.textColorLink));
        mTextColorRed = ColorUtils.rgba(ColorUtils.resolve(this, R.attr.textColorRed));

        mBackground = ColorUtils.rgba(ColorUtils.resolve(this, android.R.attr.colorBackground));
        int backgroundHighlight = ColorUtils.resolve(this, R.attr.backgroundHighlight);
        int backgroundSelection = ColorUtils.resolve(this, R.attr.backgroundSelection);
        mBackgroundHighlight = ColorUtils.rgba(backgroundHighlight);
        mBackgroundSelection = ColorUtils.rgba(backgroundSelection);
        mBackgroundHighlightSelection = ColorUtils.blend(backgroundHighlight, backgroundSelection);
    }

    protected View findHeader() {
        mAppBar = findViewById(R.id.appbar);
        if (mAppBar != null) {
            mAppBar.addOnOffsetChangedListener(this);
        }
        Toolbar toolbar = findViewById(getToolbarLayout());
        setSupportActionBar(toolbar);
        return toolbar;
    }

    public final int getCurrentPosition() {
        return mPager.getCurrentItem();
    }

    public final String getCurrentOsis() {
        if (mAdapter == null) {
            return null;
        } else {
            return mAdapter.getData(getCurrentPosition()).getString(OSIS);
        }
    }

    public final ReadingFragment getCurrentFragment() {
        if (mAdapter == null) {
            return null;
        } else {
            return mAdapter.getFragment(getCurrentPosition());
        }
    }

    public final String getCurrentVersion() {
        ReadingFragment fragment = getCurrentFragment();
        if (fragment != null) {
            return fragment.getArguments().getString(VERSION);
        } else {
            return null;
        }
    }

    protected final void prepare(int position) {
        Bundle bundle = mAdapter.getData(position);
        String osis = bundle.getString(OSIS);
        if (!TextUtils.isEmpty(osis)) {
            updateHeader(bundle, osis);
            prepareNext(position, bundle.getString(NEXT));
            preparePrev(position, bundle.getString(PREV));
        }
    }

    protected void setTheme() {
        ThemeUtils.setTheme(this);
    }

    protected void updateTaskDescription(String label) {
        if (mAppBar != null) {
            mAppBar.setExpanded(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setTaskDescription(new ActivityManager.TaskDescription(label));
        }
    }

    protected final void initialize() {
        initializeHeader(mHeader);

        mPager.setAdapter(mAdapter);
        mPager.addOnPageChangeListener(this);

        int position = getInitialPosition();
        String osis = getInitialOsis();
        Bundle bundle = retrieveOsis(position, osis);
        if (position == POSITION_UNKNOWN) {
            position = bundle.getInt(ID) - 1;
        }
        mAdapter.setData(position, bundle);
        updateVersion();
        prepare(position);

        mPager.setCurrentItem(position);
    }

    protected final void updateVersion() {
        if (versionView != null) {
            versionView.setText(bible.getVersionName(bible.getVersion()));
        }
    }

    protected final void initializeVersion(View header) {
        versionView = header.findViewById(R.id.version);
        View versionButton = header.findViewById(R.id.version_button);
        if (versionButton != null) {
            versionButton.setOnClickListener(this);
        }
    }

    protected abstract int getContentLayout();

    protected abstract int getToolbarLayout();

    protected abstract void initializeHeader(View header);

    protected abstract void updateHeader(Bundle bundle, String osis);

    protected abstract int retrieveOsisCount();

    protected abstract String getInitialOsis();

    protected abstract int getInitialPosition();

    @Override
    public Bundle retrieveOsis(int position, String osis) {
        Bundle bundle = new Bundle();
        bundle.putString(OSIS, osis);
        Uri uri = Provider.CONTENT_URI_CHAPTER.buildUpon().appendEncodedPath(osis).build();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                bundle.putInt(ID, cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)));
                String curr = getString(cursor, Provider.COLUMN_OSIS);
                bundle.putString(CURR, curr);
                bundle.putString(NEXT, getString(cursor, Provider.COLUMN_NEXT));
                bundle.putString(PREV, getString(cursor, Provider.COLUMN_PREVIOUS));
                bundle.putString(HUMAN, getString(cursor, Provider.COLUMN_HUMAN));
                bundle.putString(CONTENT, getString(cursor, Provider.COLUMN_CONTENT));
                bundle.putString(OSIS, curr);
                bundle.putString(HIGHLIGHTED, bible.getHighlight(curr));
                bundle.putBundle(NOTES, bible.getNoteVerses(curr));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot query " + osis, e);
        }
        String version = bible.getVersion();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        bundle.putInt(FONT_SIZE, sp.getInt(FONT_SIZE + "-" + version, FONT_SIZE_DEFAULT));
        bundle.putBoolean(CROSS, sp.getBoolean(CROSS, false));
        bundle.putBoolean(SHANGTI, sp.getBoolean(SHANGTI, false));
        bundle.putString(VERSION, bible.getVersion());
        bundle.putBoolean(RED, sp.getBoolean(RED, true));
        updateBundle(bundle);
        return bundle;
    }

    private String getString(Cursor cursor, String columnName) {
        return cursor.getString(cursor.getColumnIndexOrThrow(columnName));
    }

    private void prepareNext(int position, String osis) {
        int nextPosition = position + 1;
        if (nextPosition < mAdapter.getCount()) {
            prepare(nextPosition, osis);
        }
    }

    private void preparePrev(int position, String osis) {
        int prevPosition = position - 1;
        if (prevPosition >= 0) {
            prepare(prevPosition, osis);
        }
    }

    private void prepare(int position, String osis) {
        Bundle bundle = mAdapter.getData(position);
        bundle.putString(OSIS, osis);
        bundle.putAll(retrieveOsis(position, osis));
    }

    @Override
    public void onPageScrollStateChanged(int position) {
        // do nothing
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // do nothing
    }

    @Override
    public void onPageSelected(int position) {
        prepare(position);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // FIXME
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void showAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", annotation: " + annotation);
        handler.obtainMessage(ReadingHandler.SHOW_ANNOTATION,
                new ReadingHandler.Annotation(link, annotation, getCurrentOsis())).sendToTarget();
    }

    @Override
    public void showNote(String verse) {
        LogUtils.d("show note, verse: " + verse);
        handler.obtainMessage(ReadingHandler.SHOW_NOTE, verse).sendToTarget();
    }

    @Override
    public void updateBundle(Bundle bundle) {
        bundle.putString(COLOR_TEXT, mTextColorNormal);
        bundle.putString(COLOR_LINK, mTextColorLink);
        bundle.putString(COLOR_RED, mTextColorRed);

        bundle.putString(COLOR_BACKGROUND, mBackground);
        bundle.putString(COLOR_BACKGROUND_HIGHLIGHT, mBackgroundHighlight);
        bundle.putString(COLOR_BACKGROUND_SELECTION, mBackgroundSelection);
        bundle.putString(COLOR_BACKGROUND_HIGHLIGHT_SELECTION, mBackgroundHighlightSelection);
        if (!TextUtils.isEmpty(fontPath)) {
            bundle.putString(FONT_PATH, fontPath);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.book_button) {
            select(SelectActivity.BOOK);
        } else if (id == R.id.chapter_button) {
            select(SelectActivity.CHAPTER);
        } else if (id == R.id.version_button) {
            selectVersion();
        }
    }

    private void selectVersion() {
        Intent intent = new Intent(this, SelectVersionActivity.class);
        startActivityForResult(intent, REQUEST_CODE_VERSION);
    }

    private void select(int position) {
        Intent intent = new Intent(this, SelectActivity.class);
        intent.putExtra(SelectActivity.POSITION, position);
        intent.putExtra(OSIS, getCurrentOsis());
        startActivityForResult(intent, REQUEST_CODE_SELECT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT && data != null) {
            jump(data.getStringExtra(OSIS), data.getStringExtra(VERSE));
        } else if (requestCode == REQUEST_CODE_VERSION && data != null) {
            bible.setVersion(data.getStringExtra(VERSION));
            refresh();
        }
    }

    protected void refresh() {
        int position = getCurrentPosition();
        String osis = getCurrentOsis();
        mAdapter.setData(position, retrieveOsis(position, osis));
        updateVersion();
        prepare(position);
        reload(position);
    }

    private void reload(int position) {
        reloadData(position);
        if (position > 0) {
            reloadData(position - 1);
        }
        if (position < mAdapter.getCount() - 1) {
            reloadData(position + 1);
        }
    }

    private void reloadData(int position, int verse) {
        ReadingFragment fragment = (ReadingFragment) mAdapter.instantiateItem(mPager, position);
        if (verse > 0) {
            fragment.setForceVerse(verse);
        }
        Bundle bundle = fragment.getArguments();
        if (bundle != null) {
            bundle.putAll(mAdapter.getData(position));
            fragment.reloadData();
        }
    }

    private void reloadData(int position) {
        reloadData(position, 0);
    }

    private void jump(String osis, String verse) {
        Bundle bundle = retrieveOsis(POSITION_UNKNOWN, osis);
        bundle.putString(VERSE, verse);
        bundle.putString(VERSE_START, verse);
        int oldPosition = getCurrentPosition();
        int position = bundle.getInt(ID) - 1;
        mAdapter.setData(position, bundle);
        prepare(position);

        mPager.setCurrentItem(position);

        // if it's cached, then reloaded
        if (Math.abs(oldPosition - position) <= mPager.getOffscreenPageLimit()) {
            reloadData(position, NumberUtils.parseInt(verse));
        }
    }

    @Override
    public void onOffsetChanged(AppBarLayout appBar, int offset) {
        View decorView = getWindow().getDecorView();
        // http://stackoverflow.com/questions/31872653
        if (appBar.getHeight() + offset == 0) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            decorView.setSystemUiVisibility(0);
        }
    }

    public void onSelected(boolean highlight, String verses, String content) {
        handler.obtainMessage(ReadingHandler.SHOW_SELECTION,
                new ReadingHandler.Selection(highlight, verses, content)).sendToTarget();
    }

    @Override
    public void saveHighlight(String verses) {
        String osis = getCurrentOsis();
        LogUtils.d("osis: " + osis + ", highlight: " + verses);
        bible.saveHighlight(osis, verses);
        handler.obtainMessage(ReadingHandler.SHOW_SELECTION,
                new ReadingHandler.Selection(false, null, null)).sendToTarget();
    }

    public void setHighlight(String verses, boolean added) {
        getCurrentFragment().setHighlight(verses, added);
    }

    public void setNote(String verse, boolean added) {
        getCurrentFragment().setNote(verse, added);
    }

    public void addNotes(String verses) {
        handler.obtainMessage(ReadingHandler.ADD_NOTES, verses).sendToTarget();
    }

    public void share(ReadingHandler.Selection selection) {
        handler.obtainMessage(ReadingHandler.SHARE, selection).sendToTarget();
    }

    public String getVerse(String verses) {
        int index = verses.indexOf('-');
        if (index < 0) {
            index = verses.indexOf(',');
        }
        if (index < 0) {
            return verses;
        } else {
            return verses.substring(0, index);
        }
    }

    public void doAddNotes(String verses) {
        String osis = getCurrentOsis();
        LogUtils.d("osis: " + osis + ", update notes: " + verses);
        String verse = getVerse(verses);
        Bible.Note note = fetchNote(verse);

        final String tag = FRAGMENT_ADD_NOTES;
        FragmentManager manager = getSupportFragmentManager();
        AddNotesFragment fragment = (AddNotesFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new AddNotesFragment();
        fragment.setNote(verses, note);
        fragment.show(manager, tag);
    }

    public void saveNotes(long id, String verses, String content) {
        String osis = getCurrentOsis();
        String verse = getVerse(verses);
        bible.saveNote(id, osis, verse, verses, content);
        getCurrentFragment().getArguments().putBundle(NOTES, bible.getNoteVerses(osis));
        setNote(verse, !TextUtils.isEmpty(content));
        getCurrentFragment().selectVerses(verses, false);
    }

    public void deleteNote(long id, String verses) {
        String osis = getCurrentOsis();
        String verse = getVerse(verses);
        bible.deleteNote(id);
        getCurrentFragment().getArguments().putBundle(NOTES, bible.getNoteVerses(osis));
        setNote(verse, false);
    }

    public void doShowNote(String verse) {
        Bible.Note note = fetchNote(verse);
        if (note == null) {
            return;
        }

        final String tag = FRAGMENT_SHOW_NOTES;
        FragmentManager manager = getSupportFragmentManager();
        ShowNotesFragment fragment = (ShowNotesFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new ShowNotesFragment();
        fragment.setNote(note);
        fragment.show(manager, tag);
    }

    public Bible.Note fetchNote(String verse) {
        long id = getCurrentFragment().getNote(verse);
        if (id > 0) {
            return bible.getNote(id);
        } else {
            return null;
        }
    }

    public void doShowAnnotation(ReadingHandler.Annotation annotation) {
        String message = annotation.getMessage();
        if (TextUtils.isEmpty(message)) {
            message = bible.getAnnotation(annotation.getOsis(), annotation.getLink());
        }
        if (message != null) {
            doShowAnnotation(annotation.getLink(), message);
        }
    }

    private void doShowAnnotation(String link, String annotation) {
        LogUtils.d("link: " + link + ", content: " + annotation);

        final String tag = FRAGMENT_SHOW_ANNOTATIONS;
        FragmentManager manager = getSupportFragmentManager();
        ShowAnnotationFragment fragment = (ShowAnnotationFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new ShowAnnotationFragment();
        fragment.setAnnotation(link, annotation);
        fragment.show(manager, tag);
    }

    public void doShowSelection(ReadingHandler.Selection selection) {
        if (selection == null || TextUtils.isEmpty(selection.getVerses())) {
            if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
            }
        } else {
            if (actionMode != null) {
                actionMode.setTag(selection);
                actionMode.invalidate();
            } else {
                ActionMode.Callback callback = new ReadingHandler.SelectionActionMode(this,
                        selection);
                actionMode = startSupportActionMode(callback);
            }
        }
    }

    public void doShare(ReadingHandler.Selection selection) {
        String text = bible.getVersionFullname(bible.getVersion()) + " "
                + getCurrentFragment().getTitle() + ":"
                + selection.getVerses() + "\n\n"
                + selection.getContent();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.setType("text/plain");
        ChooserUtils.startActivityExcludeSelf(this, intent, getString(R.string.share));
    }

    public void updateFontSize(int fontSize) {
        int position = getCurrentPosition();
        updateFontSize(position, fontSize);
        updateFontSize(position - 1, fontSize);
        updateFontSize(position + 1, fontSize);
    }

    private void updateFontSize(int position, int fontSize) {
        if (position > 0 && position < mAdapter.getCount()) {
            ReadingFragment fragment = mAdapter.getFragment(position);
            if (fragment != null) {
                fragment.updateFontSize(fontSize);
            }
        }
    }

}