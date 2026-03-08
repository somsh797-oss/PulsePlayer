package com.pulse.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.content.res.ColorStateList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerView extends FrameLayout {

    public interface PlayerCallback {
        void onPlayPause();
        void onNext();
        void onPrev();
        void onSeekForward5();
        void onSeekBackward5();
        void onSeekTo(int position);
        void onVolumeChange(float vol);
        void onSongSelected(int index);
        void onSearch(String query);
    }

    // Colors
    private static final int BG = 0xFF0a0a0f;
    private static final int SURFACE = 0xFF13131c;
    private static final int SURFACE2 = 0xFF1c1c2a;
    private static final int ACCENT = 0xFFe8ff47;
    private static final int ACCENT2 = 0xFFff6b35;
    private static final int TEXT = 0xFFf0f0f8;
    private static final int MUTED = 0xFF5a5a7a;
    private static final int BORDER = 0x18ffffff;

    private PlayerCallback callback;
    private Context ctx;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor = Executors.newCachedThreadPool();

    // Tabs
    private LinearLayout tabBar;
    private TextView tabPlayer, tabLibrary;
    private FrameLayout playerPage, libraryPage;

    // Player page views
    private ImageView artView;
    private TextView artPlaceholder;
    private TextView titleView;
    private TextView metaView;
    private TextView timeCur, timeTotal;
    private SeekBar seekBar;
    private SeekBar volumeBar;
    private RoundedButton btnPrev, btnBack5, btnPlay, btnFwd5, btnNext;

    // Library page views
    private EditText searchView;
    private LinearLayout songListLayout;
    private ScrollView songScroll;
    private TextView libCountView;
    private ProgressBar loadingBar;

    private List<Song> currentSongs = new ArrayList<>();
    private int currentIdx = -1;
    private boolean isPlaying = false;

    public PlayerView(Context ctx, PlayerCallback callback) {
        super(ctx);
        this.ctx = ctx;
        this.callback = callback;
        setBackgroundColor(BG);
        buildUI();
    }

    // ─────────────────────────────────────────────
    //  UI CONSTRUCTION
    // ─────────────────────────────────────────────

    private void buildUI() {
        // Root vertical layout
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Tab bar
        tabBar = buildTabBar(root);

        // Page container
        FrameLayout pages = new FrameLayout(ctx);
        root.addView(pages, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        playerPage = buildPlayerPage();
        pages.addView(playerPage, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        libraryPage = buildLibraryPage();
        libraryPage.setVisibility(GONE);
        pages.addView(libraryPage, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout buildTabBar(LinearLayout root) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(BG);
        bar.setPadding(dp(12), dp(10), dp(12), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        tabPlayer = makeTab("NOW PLAYING", true);
        tabLibrary = makeTab("LIBRARY", false);

        bar.addView(tabPlayer, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(tabLibrary, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        tabPlayer.setOnClickListener(v -> showTab(true));
        tabLibrary.setOnClickListener(v -> showTab(false));

        return bar;
    }

    private TextView makeTab(String label, boolean active) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.10f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));
        tv.setTextColor(active ? ACCENT : MUTED);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(12),dp(12),dp(12),dp(12),0,0,0,0});
        bg.setColor(active ? SURFACE : Color.TRANSPARENT);
        tv.setBackground(bg);
        return tv;
    }

    private void showTab(boolean playerTab) {
        updateTabStyle(tabPlayer, playerTab);
        updateTabStyle(tabLibrary, !playerTab);
        playerPage.setVisibility(playerTab ? VISIBLE : GONE);
        libraryPage.setVisibility(playerTab ? GONE : VISIBLE);
    }

    private void updateTabStyle(TextView tv, boolean active) {
        tv.setTextColor(active ? ACCENT : MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(12),dp(12),dp(12),dp(12),0,0,0,0});
        bg.setColor(active ? SURFACE : Color.TRANSPARENT);
        tv.setBackground(bg);
    }

    // ── PLAYER PAGE ──
    private FrameLayout buildPlayerPage() {
        FrameLayout page = new FrameLayout(ctx);
        page.setBackgroundColor(SURFACE);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(16), dp(20), dp(20));
        page.addView(col, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Album art card
        FrameLayout artCard = new FrameLayout(ctx);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setCornerRadius(dp(18));
        artBg.setColor(SURFACE2);
        artCard.setBackground(artBg);
        artCard.setClipToOutline(true);

        artView = new ImageView(ctx);
        artView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artView.setVisibility(GONE);
        artCard.addView(artView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        artPlaceholder = new TextView(ctx);
        artPlaceholder.setText("🎵");
        artPlaceholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        artPlaceholder.setGravity(Gravity.CENTER);
        artCard.addView(artPlaceholder, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Square aspect ratio
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        artParams.weight = 1;
        artParams.bottomMargin = dp(20);
        col.addView(artCard, artParams);

        // Make art card square by measuring width
        artCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            if (w > 0 && artCard.getHeight() != w) {
                ViewGroup.LayoutParams lp = artCard.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams)lp).weight = 0;
                    lp.height = w;
                    artCard.setLayoutParams(lp);
                }
            }
        });

        // Song title
        titleView = new TextView(ctx);
        titleView.setText("Select a song from Library");
        titleView.setTextColor(TEXT);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setSingleLine(true);
        titleView.setSelected(true);
        col.addView(titleView, row(0, dp(2)));

        // Meta
        metaView = new TextView(ctx);
        metaView.setText("Open Library tab →");
        metaView.setTextColor(MUTED);
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        metaView.setSingleLine(true);
        col.addView(metaView, row(dp(0), dp(16)));

        // Time row
        LinearLayout timeRow = new LinearLayout(ctx);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(timeRow, row(0, dp(6)));

        timeCur = new TextView(ctx);
        timeCur.setText("0:00");
        timeCur.setTextColor(MUTED);
        timeCur.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        timeCur.setTypeface(Typeface.MONOSPACE);
        timeRow.addView(timeCur);

        View spacer = new View(ctx);
        timeRow.addView(spacer, new LinearLayout.LayoutParams(0,1,1));

        timeTotal = new TextView(ctx);
        timeTotal.setText("0:00");
        timeTotal.setTextColor(MUTED);
        timeTotal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        timeTotal.setTypeface(Typeface.MONOSPACE);
        timeRow.addView(timeTotal);

        // Seek bar
        seekBar = new SeekBar(ctx);
        seekBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (callback != null) callback.onSeekTo(s.getProgress());
            }
        });
        col.addView(seekBar, row(0, dp(18)));

        // Controls row
        LinearLayout controls = new LinearLayout(ctx);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(controls, row(0, dp(16)));

        btnPrev = new RoundedButton(ctx, "⏮", false);
        btnBack5 = new RoundedButton(ctx, "↺\n5s", false);
        btnPlay = new RoundedButton(ctx, "▶", true);
        btnFwd5 = new RoundedButton(ctx, "↻\n5s", false);
        btnNext = new RoundedButton(ctx, "⏭", false);

        LinearLayout.LayoutParams smallBtnP = new LinearLayout.LayoutParams(0, dp(56), 1f);
        LinearLayout.LayoutParams bigBtnP = new LinearLayout.LayoutParams(dp(68), dp(68));
        smallBtnP.setMargins(dp(4), 0, dp(4), 0);

        controls.addView(btnPrev, smallBtnP);
        controls.addView(btnBack5, smallBtnP);

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(68), dp(68));
        pp.setMargins(dp(8), 0, dp(8), 0);
        controls.addView(btnPlay, pp);

        controls.addView(btnFwd5, smallBtnP);
        controls.addView(btnNext, smallBtnP);

        btnPrev.setOnClickListener(v -> { if(callback!=null) callback.onPrev(); });
        btnBack5.setOnClickListener(v -> { if(callback!=null) callback.onSeekBackward5(); });
        btnPlay.setOnClickListener(v -> { if(callback!=null) callback.onPlayPause(); });
        btnFwd5.setOnClickListener(v -> { if(callback!=null) callback.onSeekForward5(); });
        btnNext.setOnClickListener(v -> { if(callback!=null) callback.onNext(); });

        // Volume row
        LinearLayout volRow = new LinearLayout(ctx);
        volRow.setOrientation(LinearLayout.HORIZONTAL);
        volRow.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(volRow, row(0, 0));

        TextView volL = new TextView(ctx);
        volL.setText("🔈");
        volL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        volRow.addView(volL, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        volumeBar = new SeekBar(ctx);
        volumeBar.setMax(100);
        volumeBar.setProgress(80);
        volumeBar.setProgressTintList(ColorStateList.valueOf(MUTED));
        volumeBar.setThumbTintList(ColorStateList.valueOf(TEXT));
        LinearLayout.LayoutParams volP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        volP.setMargins(dp(10), 0, dp(10), 0);
        volRow.addView(volumeBar, volP);

        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && callback != null) callback.onVolumeChange(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        TextView volR = new TextView(ctx);
        volR.setText("🔊");
        volR.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        volRow.addView(volR, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return page;
    }

    // ── LIBRARY PAGE ──
    private FrameLayout buildLibraryPage() {
        FrameLayout page = new FrameLayout(ctx);
        page.setBackgroundColor(SURFACE);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        page.addView(col, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(12));
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(SURFACE);

        LinearLayout hLeft = new LinearLayout(ctx);
        hLeft.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hLeftP = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        header.addView(hLeft, hLeftP);

        TextView hTitle = new TextView(ctx);
        hTitle.setText("Library");
        hTitle.setTextColor(TEXT);
        hTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hLeft.addView(hTitle);

        libCountView = new TextView(ctx);
        libCountView.setText("Scanning...");
        libCountView.setTextColor(MUTED);
        libCountView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        libCountView.setTypeface(Typeface.MONOSPACE);
        hLeft.addView(libCountView);

        col.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Divider
        View div = new View(ctx);
        div.setBackgroundColor(BORDER);
        col.addView(div, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // Search
        LinearLayout searchWrap = new LinearLayout(ctx);
        searchWrap.setPadding(dp(14), dp(10), dp(14), dp(6));
        searchWrap.setBackgroundColor(SURFACE);
        col.addView(searchWrap, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        searchView = new EditText(ctx);
        searchView.setHint("Search songs...");
        searchView.setHintTextColor(MUTED);
        searchView.setTextColor(TEXT);
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        searchView.setTypeface(Typeface.MONOSPACE);
        searchView.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchView.setBackground(makeRounded(SURFACE2, dp(12)));
        searchView.setSingleLine(true);
        GradientDrawable searchBg = makeRounded(SURFACE2, dp(12));
        searchView.setBackground(searchBg);

        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (callback != null) callback.onSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchWrap.addView(searchView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Loading bar
        loadingBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        loadingBar.setIndeterminate(true);
        loadingBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        loadingBar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        loadingBar.setVisibility(VISIBLE);
        col.addView(loadingBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        // Song list
        songScroll = new ScrollView(ctx);
        songScroll.setBackgroundColor(SURFACE);
        col.addView(songScroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        songListLayout = new LinearLayout(ctx);
        songListLayout.setOrientation(LinearLayout.VERTICAL);
        songScroll.addView(songListLayout, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        return page;
    }

    // ─────────────────────────────────────────────
    //  PUBLIC UPDATE METHODS
    // ─────────────────────────────────────────────

    public void showLoading(boolean loading) {
        handler.post(() -> loadingBar.setVisibility(loading ? VISIBLE : GONE));
    }

    public void showPermissionDenied() {
        handler.post(() -> {
            libCountView.setText("Permission denied");
            TextView msg = new TextView(ctx);
            msg.setText("Storage permission is required.\nPlease grant it in Settings.");
            msg.setTextColor(MUTED);
            msg.setGravity(Gravity.CENTER);
            msg.setPadding(dp(24), dp(40), dp(24), dp(40));
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            songListLayout.addView(msg);
        });
    }

    public void setSongs(List<Song> songs, int activeIdx) {
        handler.post(() -> {
            currentSongs = songs;
            currentIdx = activeIdx;
            libCountView.setText(songs.size() + " songs");
            renderSongList();
        });
    }

    public void setCurrentSong(Song s, int idx, int total) {
        handler.post(() -> {
            currentIdx = idx;
            titleView.setText(s.title);
            metaView.setText(s.artist + " · " + s.album + "  (" + (idx+1) + "/" + total + ")");
            timeTotal.setText(s.getFormattedDuration());
            timeCur.setText("0:00");
            seekBar.setProgress(0);
            // Update list highlight
            renderSongList();
            // Load album art async
            loadAlbumArt(s);
        });
    }

    public void updateProgress(int position, int duration) {
        handler.post(() -> {
            if (duration > 0) {
                seekBar.setProgress((int)((position / (float)duration) * 1000));
                timeCur.setText(fmtMs(position));
                timeTotal.setText(fmtMs(duration));
            }
        });
    }

    public void updatePlayState(boolean playing) {
        handler.post(() -> {
            isPlaying = playing;
            btnPlay.setLabel(playing ? "⏸" : "▶");
        });
    }

    // ─────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private void renderSongList() {
        songListLayout.removeAllViews();
        if (currentSongs.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("🎼\n\nNo songs found.\nCheck storage permission.");
            empty.setTextColor(MUTED);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(24), dp(60), dp(24), dp(60));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            songListLayout.addView(empty);
            return;
        }

        for (int i = 0; i < currentSongs.size(); i++) {
            Song s = currentSongs.get(i);
            final int idx = i;
            boolean active = (i == currentIdx);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (active) row.setBackgroundColor(0x12e8ff47);

            // Number
            TextView num = new TextView(ctx);
            num.setText(String.valueOf(i + 1));
            num.setTextColor(active ? ACCENT : MUTED);
            num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            num.setTypeface(Typeface.MONOSPACE);
            num.setMinWidth(dp(28));
            num.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(num, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            // Icon
            FrameLayout icon = new FrameLayout(ctx);
            GradientDrawable iconBg = makeRounded(active ? 0x22e8ff47 : SURFACE2, dp(10));
            if (active) iconBg.setStroke(dp(1), 0x44e8ff47);
            icon.setBackground(iconBg);
            LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(dp(42), dp(42));
            iconP.setMargins(dp(10), 0, dp(10), 0);

            TextView iconTv = new TextView(ctx);
            iconTv.setText(active ? "▶" : "♪");
            iconTv.setTextColor(active ? ACCENT : MUTED);
            iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, active ? 14 : 16);
            iconTv.setGravity(Gravity.CENTER);
            icon.addView(iconTv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            row.addView(icon, iconP);

            // Text column
            LinearLayout text = new LinearLayout(ctx);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView nameView = new TextView(ctx);
            nameView.setText(s.title);
            nameView.setTextColor(active ? ACCENT : TEXT);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameView.setTypeface(Typeface.DEFAULT_BOLD);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setSingleLine(true);
            text.addView(nameView);

            TextView durView = new TextView(ctx);
            durView.setText(s.artist + " · " + s.getFormattedDuration());
            durView.setTextColor(MUTED);
            durView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            durView.setTypeface(Typeface.MONOSPACE);
            durView.setEllipsize(TextUtils.TruncateAt.END);
            durView.setSingleLine(true);
            text.addView(durView);

            row.addView(text);

            // Divider
            View divider = new View(ctx);
            divider.setBackgroundColor(BORDER);

            final int fi = i;
            row.setOnClickListener(v -> {
                if (callback != null) callback.onSongSelected(fi);
                showTab(true);
            });

            songListLayout.addView(row);
            songListLayout.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private void loadAlbumArt(Song s) {
        executor.execute(() -> {
            try {
                InputStream is = ctx.getContentResolver().openInputStream(s.albumArtUri);
                if (is != null) {
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    handler.post(() -> {
                        if (bmp != null) {
                            artView.setImageBitmap(bmp);
                            artView.setVisibility(VISIBLE);
                            artPlaceholder.setVisibility(GONE);
                        } else {
                            artView.setVisibility(GONE);
                            artPlaceholder.setVisibility(VISIBLE);
                        }
                    });
                } else {
                    handler.post(() -> {
                        artView.setVisibility(GONE);
                        artPlaceholder.setVisibility(VISIBLE);
                    });
                }
            } catch (Exception e) {
                handler.post(() -> {
                    artView.setVisibility(GONE);
                    artPlaceholder.setVisibility(VISIBLE);
                });
            }
        });
    }

    private GradientDrawable makeRounded(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private LinearLayout.LayoutParams row(int topM, int botM) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = topM;
        p.bottomMargin = botM;
        return p;
    }

    private int dp(int v) {
        return (int)(v * ctx.getResources().getDisplayMetrics().density);
    }

    private String fmtMs(int ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s/60, s%60);
    }

    // ─────────────────────────────────────────────
    //  CUSTOM BUTTON
    // ─────────────────────────────────────────────
    private static class RoundedButton extends FrameLayout {
        private TextView label;
        private boolean isAccent;

        RoundedButton(Context ctx, String text, boolean accent) {
            super(ctx);
            this.isAccent = accent;
            setClickable(true);
            setFocusable(true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(accent ? 0xFFe8ff47 : 0xFF1c1c2a);
            bg.setCornerRadius(accent ? 200 : (int)(14 * ctx.getResources().getDisplayMetrics().density));
            bg.setStroke((int)(ctx.getResources().getDisplayMetrics().density), 0x18ffffff);
            setBackground(bg);

            if (accent) {
                setElevation(8 * ctx.getResources().getDisplayMetrics().density);
            }

            label = new TextView(ctx);
            label.setText(text);
            label.setTextColor(accent ? 0xFF0a0a0f : 0xFFf0f0f8);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, accent ? 24 : 18);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(false);
            addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    setScaleX(0.92f); setScaleY(0.92f);
                } else if (e.getAction() == MotionEvent.ACTION_UP
                    || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    setScaleX(1f); setScaleY(1f);
                }
                return false;
            });
        }

        void setLabel(String text) {
            label.setText(text);
        }
    }
}
