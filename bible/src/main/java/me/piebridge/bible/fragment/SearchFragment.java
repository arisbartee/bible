package me.piebridge.bible.fragment;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import me.piebridge.bible.Bible;
import me.piebridge.bible.R;
import me.piebridge.bible.activity.SearchActivity;
import me.piebridge.bible.preference.VersionPreference;

/**
 * Created by thom on 2017/6/26.
 */
public class SearchFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private VersionPreference preferenceVersion;

    private CheckBoxPreference preferenceSearchAll;
    private CheckBoxPreference preferenceSearchOld;
    private CheckBoxPreference preferenceSearchNew;
    private CheckBoxPreference preferenceSearchGospel;

    private ListPreference preferenceSearchFrom;
    private ListPreference preferenceSearchTo;

    private static final int SEARCH_ALL = 0;
    private static final int SEARCH_OLD = 1;
    private static final int SEARCH_NEW = 2;
    private static final int SEARCH_GOSPEL = 3;
    private static final int SEARCH_CUSTOM = 4;

    public static final String KEY_SEARCH_ALL = "search_all";
    public static final String KEY_SEARCH_OLD = "search_old";
    public static final String KEY_SEARCH_NEW = "search_new";
    public static final String KEY_SEARCH_GOSPEL = "search_gospel";
    public static final String KEY_SEARCH_FROM = "search_from";
    public static final String KEY_SEARCH_TO = "search_to";

    private static final String OLD_FIRST = "Gen";
    private static final String OLD_LAST = "Mal";

    private static final String NEW_FIRST = "Matt";
    private static final String NEW_LAST = "Rev";

    private static final String GOSPEL_FIRST = "Matt";
    private static final String GOSPEL_LAST = "John";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.search, rootKey);

        preferenceVersion = (VersionPreference) findPreference("version");
        setListener(preferenceVersion);

        preferenceSearchAll = (CheckBoxPreference) findPreference(KEY_SEARCH_ALL);
        preferenceSearchOld = (CheckBoxPreference) findPreference(KEY_SEARCH_OLD);
        preferenceSearchNew = (CheckBoxPreference) findPreference(KEY_SEARCH_NEW);
        preferenceSearchGospel = (CheckBoxPreference) findPreference(KEY_SEARCH_GOSPEL);
        setListener(preferenceSearchAll, preferenceSearchOld, preferenceSearchNew, preferenceSearchGospel);

        preferenceSearchFrom = (ListPreference) findPreference(KEY_SEARCH_FROM);
        preferenceSearchTo = (ListPreference) findPreference(KEY_SEARCH_TO);
        setListener(preferenceSearchFrom, preferenceSearchTo);

        updateVersion();
    }

    private void setListener(Preference... preferences) {
        for (Preference preference : preferences) {
            preference.setOnPreferenceChangeListener(this);
            preference.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        switch (key) {
            case "version":
                updateVersion();
                break;
            default:
                if (!Boolean.FALSE.equals(newValue)) {
                    updateSearch(preference, newValue);
                } else {
                    return false;
                }
                break;
        }
        return true;
    }

    private void updateSearch(Preference preference, Object newValue) {
        SearchActivity activity = (SearchActivity) getActivity();
        if (activity == null) {
            return;
        }

        Bible bible = Bible.getInstance(activity.getApplication());

        String key = preference.getKey();
        String osisFrom;
        String osisTo;

        String osisFirst = bible.get(Bible.TYPE.OSIS, 0);
        String osisLast = bible.get(Bible.TYPE.OSIS, bible.getCount(Bible.TYPE.OSIS) - 1);

        switch (key) {
            case KEY_SEARCH_ALL:
                osisFrom = osisFirst;
                osisTo = osisLast;
                break;
            case KEY_SEARCH_OLD:
                osisFrom = OLD_FIRST;
                osisTo = OLD_LAST;
                break;
            case KEY_SEARCH_NEW:
                osisFrom = NEW_FIRST;
                osisTo = NEW_LAST;
                break;
            case KEY_SEARCH_GOSPEL:
                osisFrom = GOSPEL_FIRST;
                osisTo = GOSPEL_LAST;
                break;
            case KEY_SEARCH_FROM:
                osisFrom = (String) newValue;
                osisTo = preferenceSearchTo.getValue();
                break;
            case KEY_SEARCH_TO:
                osisFrom = preferenceSearchFrom.getValue();
                osisTo = (String) newValue;
                break;
            default:
                return;
        }

        int searchType;
        if (osisFrom.equals(osisFirst) && osisTo.equals(osisLast)) {
            searchType = SEARCH_ALL;
        } else if (osisFrom.equals(OLD_FIRST) && osisTo.equals(OLD_LAST)) {
            searchType = SEARCH_OLD;
        } else if (osisFrom.equals(NEW_FIRST) && osisTo.equals(NEW_LAST)) {
            searchType = SEARCH_NEW;
        } else if (osisFrom.equals(GOSPEL_FIRST) && osisTo.equals(GOSPEL_LAST)) {
            searchType = SEARCH_GOSPEL;
        } else {
            searchType = SEARCH_CUSTOM;
        }

        preferenceSearchAll.setChecked(searchType == SEARCH_ALL);
        preferenceSearchOld.setChecked(searchType == SEARCH_OLD);
        preferenceSearchNew.setChecked(searchType == SEARCH_NEW);
        preferenceSearchGospel.setChecked(searchType == SEARCH_GOSPEL);

        preferenceSearchFrom.setValue(osisFrom);
        preferenceSearchTo.setSummary(bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, osisFrom)));

        preferenceSearchTo.setValue(osisTo);
        preferenceSearchTo.setSummary(bible.get(Bible.TYPE.HUMAN, bible.getPosition(Bible.TYPE.OSIS, osisTo)));
    }

    private void updateVersion() {
        SearchActivity activity = (SearchActivity) getActivity();
        if (activity == null) {
            return;
        }
        Bible bible = Bible.getInstance(activity.getApplication());

        String[] osiss = bible.get(Bible.TYPE.OSIS).toArray(new String[0]);
        String[] humans = bible.get(Bible.TYPE.HUMAN).toArray(new String[0]);

        preferenceSearchFrom.setEntries(humans);
        preferenceSearchFrom.setEntryValues(osiss);
        preferenceSearchTo.setEntries(humans);
        preferenceSearchTo.setEntryValues(osiss);

        int max = bible.getCount(Bible.TYPE.OSIS) - 1;
        preferenceSearchAll.setSummary(getString(R.string.fromto,
                bible.get(Bible.TYPE.HUMAN, 0), bible.get(Bible.TYPE.HUMAN, max)));

        updateSearch(preferenceSearchOld, bible, OLD_FIRST, OLD_LAST);
        updateSearch(preferenceSearchNew, bible, NEW_FIRST, NEW_LAST);
        updateSearch(preferenceSearchGospel, bible, GOSPEL_FIRST, GOSPEL_LAST);
    }

    private void updateSearch(Preference preference, Bible bible, String osisFirst, String osisLast) {
        int first = bible.getPosition(Bible.TYPE.OSIS, osisFirst);
        int last = bible.getPosition(Bible.TYPE.OSIS, osisLast);
        if (first != -1 && last != -1) {
            preference.setSummary(getString(R.string.fromto,
                    bible.get(Bible.TYPE.HUMAN, first), bible.get(Bible.TYPE.HUMAN, last)));
        } else {
            preference.setEnabled(false);
            preference.setSummary("");
        }
    }

    public void updateVersion(String version) {
        if (preferenceVersion != null) {
            preferenceVersion.setValue(version);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        SearchActivity searchActivity = (SearchActivity) getActivity();
        searchActivity.hideSoftInput();
        return false;
    }

}