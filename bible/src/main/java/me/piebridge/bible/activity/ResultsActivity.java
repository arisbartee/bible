package me.piebridge.bible.activity;

import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

import me.piebridge.bible.Bible;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.Provider;
import me.piebridge.bible.R;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.ColorUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/24.
 */
public class ResultsActivity extends ToolbarActivity implements View.OnClickListener {

    private static final int SEARCH = 0;
    private static final int RESULTS = 1;

    private static final int REQUEST_CODE_VERSION = 1002;

    Bible bible;

    private TextView versionView;

    private RecyclerView recyclerView;

    private WorkHandler workHandler;

    private String mBooks;

    private String mQuery;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);
        showBack(true);

        bible = Bible.getInstance(getApplication());

        versionView = findViewById(R.id.version);
        findViewById(R.id.version_button).setOnClickListener(this);

        recyclerView = findViewById(R.id.recycler);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));

        workHandler = new WorkHandler(this, new MainHandler(this));

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        mQuery = intent.getStringExtra(SearchManager.QUERY);
        setTitle(mQuery);
        updateTaskDescription(mQuery);
        updateVersion();
        refresh();
    }

    private void refresh() {
        workHandler.obtainMessage(SEARCH, mQuery).sendToTarget();
    }

    protected final void updateVersion() {
        versionView.setText(bible.getVersionName(bible.getVersion()));
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.version_button) {
            Intent intent = new Intent(this, SelectVersionActivity.class);
            startActivityForResult(intent, REQUEST_CODE_VERSION);
        } else if (v instanceof CardView) {
            OsisItem item = (OsisItem) v.getTag();
            LogUtils.d("show item: " + item);
            if (item != null) {
                showItem(item);
            }
        }
    }

    private void showItem(OsisItem item) {
        Intent intent = new Intent(this, ReadingItemsActivity.class);
        ArrayList<OsisItem> items = new ArrayList<>();
        if (TextUtils.isEmpty(item.chapter)) {
            String[] chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, item.book)).split(",");
            for (String chapter : chapters) {
                items.add(new OsisItem(item.book, chapter));
            }
        } else {
            items.add(item);
        }
        intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, items);
        intent.putExtra(ReadingItemsActivity.SEARCH, mQuery);
        startActivity(setFinished(intent, false));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_VERSION && data != null) {
            bible.setVersion(data.getStringExtra(AbstractReadingActivity.VERSION));
            updateVersion();
            refresh();
        }
    }

    @Override
    public Intent getSupportParentActivityIntent() {
        Intent intent = super.getSupportParentActivityIntent();
        if (intent != null) {
            intent.setAction(null);
            intent.putExtra(SearchManager.QUERY, mQuery);
        }
        return intent;
    }

    public String getBooks() {
        if (mBooks != null) {
            return mBooks;
        }
        Intent intent = getIntent();
        String osisFrom = intent.getStringExtra(SearchActivity.OSIS_FROM);
        String osisTo = intent.getStringExtra(SearchActivity.OSIS_TO);
        int fromBook = 0;
        if (!TextUtils.isEmpty(osisFrom)) {
            fromBook = bible.getPosition(Bible.TYPE.OSIS, osisFrom);
            if (fromBook == -1) {
                fromBook = 0;
            }
        }
        int toBook = -1;
        if (!TextUtils.isEmpty(osisTo)) {
            toBook = bible.getPosition(Bible.TYPE.OSIS, osisTo);
        }
        if (toBook == -1) {
            toBook = bible.getCount(Bible.TYPE.OSIS) - 1;
        }
        if (toBook < fromBook) {
            int swap = fromBook;
            fromBook = toBook;
            toBook = swap;
        }
        StringBuilder books = new StringBuilder();
        if (fromBook > 0 || toBook < bible.getCount(Bible.TYPE.OSIS) - 1) {
            books.append(String.format("'%s'", bible.get(Bible.TYPE.OSIS, fromBook)));
            for (int i = fromBook + 1; i <= toBook; ++i) {
                books.append(String.format(", '%s'", bible.get(Bible.TYPE.OSIS, i)));
            }
        }
        mBooks = books.toString();
        return mBooks;
    }

    private void showResults(Cursor[] cursors) {
        if (cursors[0] == null && cursors[1] == null) {
            recyclerView.setAdapter(null);
        } else {
            int color = ColorUtils.resolve(this, R.attr.backgroundSelection);
            recyclerView.setAdapter(new ResultAdapter(this, cursors, mQuery, color));
        }
    }

    @Override
    public void finish() {
        RecyclerView.Adapter adapter = recyclerView.getAdapter();
        if (adapter instanceof ResultAdapter) {
            ((ResultAdapter) adapter).close();
        }
        super.finish();
    }

    String getChapter(String book, int chapterId) {
        String[] chapters = bible.get(Bible.TYPE.CHAPTER, bible.getPosition(Bible.TYPE.OSIS, book)).split(",");
        String chapter;
        if (chapterId < 1) {
            chapter = chapters[0];
        } else if (chapterId > chapters.length) {
            chapter = chapters[chapters.length - 1];
        } else {
            chapter = chapters[chapterId - 1];
        }
        return chapter;
    }

    static class MainHandler extends Handler {

        private final WeakReference<ResultsActivity> mReference;

        public MainHandler(ResultsActivity activity) {
            super(activity.getMainLooper());
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESULTS:
                    showResults((Cursor[]) msg.obj);
                    break;
            }
        }

        private void showResults(Cursor[] cursors) {
            ResultsActivity activity = mReference.get();
            if (activity != null) {
                activity.showResults(cursors);
            }
        }

    }

    static class WorkHandler extends Handler {

        private final Handler mainHandler;
        private final WeakReference<ResultsActivity> mReference;

        WorkHandler(ResultsActivity activity, Handler handler) {
            super(newLooper());
            mainHandler = handler;
            mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Search");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SEARCH:
                    doSearch((String) msg.obj);
                    break;
            }
        }

        private void doSearch(String query) {
            ResultsActivity activity = mReference.get();
            if (activity != null) {
                Uri bookUri = Provider.CONTENT_URI_BOOK.buildUpon()
                        .appendQueryParameter("books", activity.getBooks())
                        .appendEncodedPath(query).build();
                Cursor bookCursor = activity.getContentResolver().query(bookUri, null, null, null, null);

                Uri verseUri = Provider.CONTENT_URI_SEARCH.buildUpon()
                        .appendQueryParameter("books", activity.getBooks())
                        .appendEncodedPath(query).build();
                Cursor verseCursor = activity.getContentResolver().query(verseUri, null, null, null, null);

                mainHandler.sendMessage(mainHandler.obtainMessage(RESULTS, new Cursor[] {
                        bookCursor, verseCursor
                }));
            }
        }

    }

    private static class VerseViewHolder extends RecyclerView.ViewHolder {

        final CardView cardView;

        final TextView humanView;

        final TextView verseView;

        final TextView unformattedView;

        public VerseViewHolder(CardView view) {
            super(view);
            this.cardView = view;
            this.humanView = view.findViewById(R.id.human);
            this.verseView = view.findViewById(R.id.verse);
            this.unformattedView = view.findViewById(R.id.unformatted);
        }
    }

    private static class BookViewHolder extends RecyclerView.ViewHolder {

        final CardView cardView;

        final TextView bookView;

        public BookViewHolder(CardView view) {
            super(view);
            this.cardView = view;
            this.bookView = view.findViewById(R.id.book);
        }

    }


    private static class ResultAdapter extends RecyclerView.Adapter {

        private static final int TYPE_COUNT = 0;
        private static final int TYPE_BOOK = 1;
        private static final int TYPE_VERSE = 2;

        private final ResultsActivity mActivity;

        private final Cursor bookCursor;
        private int bookStart = -1;
        private int bookEnd = -1;
        private int bookOsis;
        private int bookHuman;
        private int bookChapters;

        private final Cursor verseCursor;
        private int verseStart = -1;
        private int verseEnd = -1;
        private int verseBook;
        private int verseHuman;
        private int verseVerse;
        private int verseUnformatted;

        private final String mQuery;
        private final int mColor;

        public ResultAdapter(ResultsActivity activity, Cursor[] cursors, String query, int color) {
            this.mActivity = activity;
            this.mQuery = query.toLowerCase(Locale.US);
            this.mColor = color;

            this.bookCursor = cursors[0];
            this.verseCursor = cursors[1];

            if (bookCursor != null) {
                bookStart = 0;
                bookEnd = bookStart + bookCursor.getCount();

                bookOsis = bookCursor.getColumnIndex(Provider.COLUMN_OSIS);
                bookHuman = bookCursor.getColumnIndex(Provider.COLUMN_HUMAN);
                bookChapters = bookCursor.getColumnIndex(Provider.COLUMN_CHAPTERS);
            }

            if (verseCursor != null) {
                verseStart = bookEnd + 1;
                verseEnd = verseStart + verseCursor.getCount();

                verseBook = verseCursor.getColumnIndexOrThrow(Provider.COLUMN_BOOK);
                verseHuman = verseCursor.getColumnIndexOrThrow(Provider.COLUMN_HUMAN);
                verseVerse = verseCursor.getColumnIndexOrThrow(Provider.COLUMN_VERSE);
                verseUnformatted = verseCursor.getColumnIndexOrThrow(Provider.COLUMN_UNFORMATTED);
            }

            LogUtils.d("book: " + bookStart + " - " + bookEnd + ", verse: " + verseStart + " - " + verseEnd);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            CardView cardView;
            switch (viewType) {
                case TYPE_COUNT:
                    View view = inflater.inflate(R.layout.item, parent, false);
                    return new CountViewHolder(view);
                case TYPE_BOOK:
                    cardView = (CardView) inflater.inflate(R.layout.item_book, parent, false);
                    cardView.setOnClickListener(mActivity);
                    return new BookViewHolder(cardView);
                case TYPE_VERSE:
                    cardView = (CardView) inflater.inflate(R.layout.item_result, parent, false);
                    cardView.setOnClickListener(mActivity);
                    return new VerseViewHolder(cardView);
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position > verseStart && verseStart >= 0) {
                verseCursor.moveToPosition(position - verseStart - 1);
                VerseViewHolder verseViewHolder = (VerseViewHolder) holder;
                verseViewHolder.humanView.setText(verseCursor.getString(verseHuman));
                verseViewHolder.cardView.setTag(bindVerse(verseViewHolder.verseView));
                bindUnformatted(verseViewHolder.unformattedView);
            } else if (position == verseStart && verseStart >= 0) {
                CountViewHolder countViewHolder = (CountViewHolder) holder;
                countViewHolder.typeView.setText(mActivity.getString(R.string.verse));
                countViewHolder.countView.setText(mActivity.getString(R.string.count, verseEnd - verseStart));
            } else if (position > bookStart && bookStart >= 0) {
                bookCursor.moveToPosition(position - bookStart - 1);
                BookViewHolder bookViewHolder = (BookViewHolder) holder;
                String bookResult = mActivity.getString(R.string.chapters,
                        bookCursor.getString(bookHuman), bookCursor.getInt(bookChapters));
                bookViewHolder.bookView.setText(bookResult, TextView.BufferType.SPANNABLE);
                bookViewHolder.cardView.setTag(new OsisItem(bookCursor.getString(bookOsis)));
                selectQuery(bookViewHolder.bookView, bookResult);
            } else if (position == bookStart && bookStart >= 0) {
                CountViewHolder countViewHolder = (CountViewHolder) holder;
                countViewHolder.typeView.setText(mActivity.getString(R.string.book));
                countViewHolder.countView.setText(mActivity.getString(R.string.count, bookEnd - bookStart));
            }
        }

        private void bindUnformatted(TextView textView) {
            String content = verseCursor.getString(verseUnformatted);
            if (Locale.getDefault().equals(Locale.SIMPLIFIED_CHINESE)) {
                content = content.replaceAll("「", "“").replaceAll("」", "”");
                content = content.replaceAll("『", "‘").replaceAll("』", "’");
            }
            textView.setText(content, TextView.BufferType.SPANNABLE);
            selectQuery(textView, content);
        }

        private void selectQuery(TextView textView, String content) {
            Spannable span = (Spannable) textView.getText();
            String lowerContent = content.toLowerCase(Locale.US);
            int index = -1;
            int queryLength = mQuery.length();
            while ((index = lowerContent.indexOf(mQuery, index + 1)) >= 0) {
                span.setSpan(new BackgroundColorSpan(mColor), index, index + queryLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        private OsisItem bindVerse(TextView textView) {
            String verse = verseCursor.getString(verseVerse);
            int[] chapterVerse = BibleUtils.getChapterVerse(verse);
            if (chapterVerse[0] != 0) {
                String book = verseCursor.getString(verseBook);
                String chapter = mActivity.getChapter(book, chapterVerse[0]);
                String string = mActivity.getString(R.string.search_result_verse,
                        chapter,
                        chapterVerse[1]);
                textView.setText(string);
                return new OsisItem(book, chapter, Integer.toString(chapterVerse[1]));
            } else {
                textView.setText(null);
                return null;
            }
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (bookCursor != null) {
                count += bookCursor.getCount() + 1;
            }
            if (verseCursor != null) {
                count += verseCursor.getCount() + 1;
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (position > verseStart && verseStart >= 0) {
                return TYPE_VERSE;
            } else if (position == verseStart && verseStart >= 0) {
                return TYPE_COUNT;
            } else if (position > bookStart && bookStart >= 0) {
                return TYPE_BOOK;
            } else if (position == bookStart && bookStart >= 0) {
                return TYPE_COUNT;
            }
            return -1;
        }

        public void close() {
            if (bookCursor != null) {
                bookCursor.close();
            }
            if (verseCursor != null) {
                verseCursor.close();
            }
        }


    }

}