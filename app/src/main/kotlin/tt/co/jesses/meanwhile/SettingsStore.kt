package tt.co.jesses.meanwhile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** The one DataStore for the app's settings. It has to be a single top-level instance per file. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "meanwhile_settings")
