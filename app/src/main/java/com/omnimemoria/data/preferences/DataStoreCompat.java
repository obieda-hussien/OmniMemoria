package com.omnimemoria.data.preferences;

import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;

final class DataStoreCompat {
    private DataStoreCompat() {
    }

    static Preferences.Key<Boolean> booleanKey(String name) {
        return PreferencesKeys.booleanKey(name);
    }

    static Preferences.Key<String> stringKey(String name) {
        return PreferencesKeys.stringKey(name);
    }

    static Preferences withBoolean(
            Preferences source,
            Preferences.Key<Boolean> key,
            boolean value
    ) {
        MutablePreferences mutable = new MutablePreferences();
        mutable.plusAssign(source);
        mutable.set(key, value);
        return mutable;
    }

    static Preferences withString(
            Preferences source,
            Preferences.Key<String> key,
            String value
    ) {
        MutablePreferences mutable = new MutablePreferences();
        mutable.plusAssign(source);
        mutable.set(key, value);
        return mutable;
    }
}
