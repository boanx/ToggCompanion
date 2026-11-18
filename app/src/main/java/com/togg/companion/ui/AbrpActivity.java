package com.togg.companion.ui;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
/** Compatibility entry for existing notification intents. */
public final class AbrpActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_DIRECTIONS, true));
        finish();
    }
}
