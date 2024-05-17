package com.example.msplayer;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chibde.visualizer.BarVisualizer;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.jgabrielfreitas.core.BlurImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter;

public class MainActivity extends AppCompatActivity {

    //members
    RecyclerView recyclerview;
    SongAdapter songAdapter;
    List<Song> allSongs = new ArrayList<>();
    ActivityResultLauncher<String> storagePermissionLaucher;
    final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;

    //today
    ExoPlayer player;

    ActivityResultLauncher<String> recordAudioPermissionLauncher; //to be accessed in the song adapter
    final String recordAudioPermission = Manifest.permission.RECORD_AUDIO;
    ConstraintLayout playerView, homeControlWrapper,headWrapper, artworkWrapper, seekbarWrapper, controlWrapper, audioVisualizerWrapper;
    TextView songName, skipPreviousBtn, skipNextBtn, playPauseBtn, repeatModeBtn, playlistBtn, playerCloseBtn,homeSkipPrevioutButton, homeSkipNextBtn, homePlayBtn;
    //TextView  ;
    //artwork
    CircleImageView artworkView;
    SeekBar seekbar;
    TextView progressView, durationView, homeSongNameView;
    //audio visualizer
    BarVisualizer audioVisualizer;
    //blur imageview
    BlurImageView blurImageView;
    //status bar, navigation color
    int defaultStatusColor;
    //repeat mode
    int repeatMode = 1; //repeat all = 1, repeat one = 2, shuffle all = 3

    //is the act. bound?
    boolean isBound = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //save the status coloor
        defaultStatusColor = getWindow().getStatusBarColor();
        //set the navigation color
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199)); // 0 & 255

        //set the tool bar, and app title
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString((R.string.app_name)));

//        getSupportActionBar().setTitle(getResources().getString(R.string.app_name));
        //recyclerview

        recyclerview = findViewById(R.id.recyclerview);
        storagePermissionLaucher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted->{
            if (granted){
                //fetch songs
                fetchSongs();
            }
            else{
                userResponses();
            }
        });
        //lauch storage permission on create
        storagePermissionLaucher.launch(permission);
        //record audio permission
        recordAudioPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted-> {
            if (granted && player.isPlaying()){
                activateAudioVisualizer();
            }
            else{
                userResponseOnRecrdAudioPerm();
            }
        });
        //views
        //player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.playerView);
        playerCloseBtn = findViewById(R.id.playerCloseBtn);
        songName = findViewById(R.id.songNameView);
        skipPreviousBtn  = findViewById(R.id.skipPreviousBtn);
        skipNextBtn =findViewById(R.id.skipNextBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        repeatModeBtn = findViewById(R.id.repeatModeBtn);
        playlistBtn = findViewById(R.id.playlistBtn);

        homeSongNameView = findViewById(R.id.homeSongNameView);
        homeSkipPrevioutButton = findViewById(R.id.homeSkipPreviousBtn);
        homeSkipNextBtn = findViewById(R.id.homeSkipNextBtn);
        homePlayBtn = findViewById(R.id.homePlayPauseBtn);

        homeControlWrapper = findViewById(R.id.homeControlWraper);
        headWrapper = findViewById(R.id.headWrapper);
        artworkWrapper = findViewById(R.id.artworkWrapper);
        seekbarWrapper = findViewById(R.id.seekbarWraper);
        controlWrapper = findViewById(R.id.controlWrapper);
        audioVisualizerWrapper = findViewById(R.id.audioVisualizerWrapper);

        artworkView = findViewById(R.id.artworkView);

        seekbar = findViewById(R.id.seekbar);

        progressView = findViewById(R.id.progressView);

        durationView = findViewById(R.id.durationView);

        audioVisualizer = findViewById(R.id.visualizer);

        blurImageView = findViewById(R.id.blurImageView);
        //player controls method
        //playerControls();

        //bind to the player service, and do everry thing after the binding
        doBindService();

        //on back pressed
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Handle the back button event here
                // Add your custom logic
                if (playerView.getVisibility() != View.VISIBLE){
                    showConfirmationDialog();
                }

            }
        };

        OnBackPressedDispatcher onBackPressedDispatcher = MainActivity.this.getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(callback);
    }

    
    private void doBindService() {
        Intent playerServiceIntent = new Intent(this, PlayerService.class);
        bindService(playerServiceIntent, playerServiceConnection, Context.BIND_AUTO_CREATE);


    }

    ServiceConnection playerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //get the service instance
            PlayerService.ServiceBinder binder = (PlayerService.ServiceBinder) service;
            player = binder.getPlayerService().player;
            isBound = true;
            //ready to
            storagePermissionLaucher.launch(permission);
            //call player control method
            playerControls();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void showConfirmationDialog() {
        // Show a dialog to confirm the back button press
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirmation")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Perform any necessary actions before exiting the app
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }


    private void playerControls() {
        //song name marquee
        songName.setSelected(true);
        homeSongNameView.setSelected(true);
        //exit the player view
        playerCloseBtn.setOnClickListener(view -> exitPlayerView());

        playlistBtn.setOnClickListener(view -> exitPlayerView());
        //open player view on home control wrapper click
        homeControlWrapper.setOnClickListener(view-> showPlayerView());

        //player listener
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                Player.Listener.super.onMediaItemTransition(mediaItem, reason);
                //show the playing sog title
                assert mediaItem != null;
                songName.setText(mediaItem.mediaMetadata.title);
                homeSongNameView.setText(mediaItem.mediaMetadata.title);

                progressView.setText(getReadableTime((int)player.getCurrentPosition()));
                seekbar.setProgress((int) player.getCurrentPosition());
                seekbar.setMax((int)player.getDuration());
                durationView.setText(getReadableTime((int)player.getDuration()));
                playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);
                homePlayBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);
                //show the current art work
                showCurrentArtWork();
                //update the progress posion of a current playing song
                updatePlayerPositionProgress();
                //load the artwork animation
                artworkView.setAnimation(loadRotation());

                //set audio visualizer
                activateAudioVisualizer();;
                //update player view colors
                updatePlayerColors();
                if(!player.isPlaying()){
                    player.play();
                }

            }
            @Override
            public void onPlaybackStateChanged(int playbackState){
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                if(playbackState == ExoPlayer.STATE_READY){
                    // SET VALUES TO PLAYER VIEWS
                    songName.setText(Objects.requireNonNull(player.getCurrentMediaItem().mediaMetadata.title));
                    homeSongNameView.setText(Objects.requireNonNull(player.getCurrentMediaItem().mediaMetadata.title));
                    progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                    durationView.setText(getReadableTime((int) player.getDuration()));
                    seekbar.setMax((int) player.getDuration());
                    seekbar.setProgress((int) player.getCurrentPosition());
                    playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);
                    homePlayBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);

                    //show the current art work
                    showCurrentArtWork();
                    //update the progress posion of a current playing song
                    updatePlayerPositionProgress();
                    //load the artwork animation
                    artworkView.setAnimation(loadRotation());

                    //set audio visualizer
                    activateAudioVisualizer();;
                    //update player view colors
                    updatePlayerColors();
                }
                else {
                    playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play,0,0,0);
                    homePlayBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play,0,0,0);

                }
            }

        });
        //skip the next track
        skipNextBtn.setOnClickListener(view -> skipToNextSong());
        homeSkipNextBtn.setOnClickListener(view -> skipToNextSong());

        //skip previous

        skipPreviousBtn.setOnClickListener(view -> skipToPreviousSong());
        homeSkipPrevioutButton.setOnClickListener(view -> skipToPreviousSong());
        //play or pause the player

        playPauseBtn.setOnClickListener(view -> playOrPausePlayer());
        //seek bar listener
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressValue = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressValue = seekBar.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player.getPlaybackState() == ExoPlayer.STATE_READY){
                    seekBar.setProgress(progressValue);
                    progressView.setText(getReadableTime(progressValue));
                    player.seekTo(progressValue);
                }

            }
        });

        //repeat mode
        repeatModeBtn.setOnClickListener(view ->{
            if (repeatMode == 1){
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
                repeatMode = 2;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat_one_24, 0,0,0);

            }
            else if(repeatMode == 2){
                //shuffle all
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                repeatMode = 3;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.shuffle,0,0,0);

            }
            else if (repeatMode == 3){
                //repeat all
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(false);
                repeatMode = 1;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat_all,0,0,0);
            }
            //update colors
            updatePlayerColors();
        });
    }

    private void playOrPausePlayer() {
        if(player.isPlaying()){
            player.pause();
            playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play,0,0,0);
            homePlayBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play,0,0,0);
            artworkView.clearAnimation();
        }else{
            player.play();
            playPauseBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);
            homePlayBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause,0,0,0);
            artworkView.startAnimation(loadRotation());
        }
        //update player colors
        updatePlayerColors();
    }

    private void skipToNextSong() {
        if (player.hasNextMediaItem()){
            player.seekToNext();
        }
    }

    private void skipToPreviousSong() {
        if (player.hasPreviousMediaItem()){
            player.seekToPrevious();
        }
    }

    private Animation loadRotation() {
        RotateAnimation rotateAnimation = new RotateAnimation(0,360, Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(10000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);
        return rotateAnimation;
    }

    private void updatePlayerPositionProgress() {
        new Handler().postDelayed(new Runnable(){
            @Override
            public void run(){
                if (player.isPlaying()){
                    progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                    seekbar.setProgress((int)player.getCurrentPosition());
                }
                //repeat calling the method
                updatePlayerPositionProgress();
            }
        }, 1000);
    }

    private void showCurrentArtWork() {
        artworkView.setImageURI(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.artworkUri);
        if (artworkView.getDrawable() == null){
            artworkView.setImageResource(R.drawable.art);
        }
    }

    private String getReadableTime(int duration) {
        String time;
        int hrs = duration/(1000*60*60);
        int min = (duration%(1000*60*60))/(1000*60);
        int secs = (((duration%(1000*60*60))%(1000*60*60))%(1000*60))/1000;

        if(hrs<1){
            time = min +": "+secs;
        }
        else{
            time = hrs + ":"+min+":"+secs;
        }
        return time;
    }

    private void showPlayerView() {
        playerView.setVisibility(View.VISIBLE);
        updatePlayerColors();
    }

    private void updatePlayerColors() {
        //only player view is visible
        if(playerView.getVisibility() == View.GONE) return;

        BitmapDrawable bitmapDrawable = (BitmapDrawable) artworkView.getDrawable();
        if(bitmapDrawable == null){
            bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.art);

        }
        Bitmap bmp = bitmapDrawable.getBitmap();
        //set bitmap to blur image view
        blurImageView.setImageBitmap(bmp);
        blurImageView.setBlur(4);

        //player control colors
        Palette.from(bmp).generate(palette->{
            if(palette != null){
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                if(swatch == null ){
                    swatch = palette.getMutedSwatch();
                    if (swatch == null){
                        swatch = palette.getDominantSwatch();
                    }
                }
                //extract text colors
                int titleTextColor = swatch.getTitleTextColor();
                int bodyTextColor = swatch.getBodyTextColor();
                int rgbColor = swatch.getRgb();
                //set colors to players views
                //status & nvigtion bar colors
                getWindow().setStatusBarColor(rgbColor);
                getWindow().setNavigationBarColor(rgbColor);

                //moer view colors
                songName.setTextColor(titleTextColor);
                playerCloseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
                progressView.setTextColor(bodyTextColor);
                durationView.setTextColor(bodyTextColor);

                repeatModeBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipPreviousBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipNextBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                playPauseBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                playlistBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
            }
        });
    }

    private void exitPlayerView() {
        playerView.setVisibility(View.GONE);
        getWindow().setStatusBarColor(defaultStatusColor);
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199));
    }

    private void userResponseOnRecrdAudioPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(shouldShowRequestPermissionRationale(recordAudioPermission)){
                //show aneducational ui explaining why we need this permission
                //use alert dialog
                new AlertDialog.Builder(this)
                        .setTitle("Requesting to show Audio Visualizer")
                        .setMessage("Allow this app to do display Audio Visualizer when music is playing")
                        .setPositiveButton("allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //request the perm
                                recordAudioPermissionLauncher.launch(recordAudioPermission);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(getApplicationContext(), "you denied to show the audio visualizee", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
            else{
                Toast.makeText(getApplicationContext(), "you denied to show the audio visualzer", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //audio vi
    private void activateAudioVisualizer() {
        //check if we have record audio permission to show an adio visualizer
        if(ContextCompat.checkSelfPermission(this, recordAudioPermission) != PackageManager.PERMISSION_GRANTED){
            return;
        }
        //set color to the audio visualizer
        audioVisualizer.setColor(ContextCompat.getColor(this, R.color.secondary_color));
        //set number of visualizer btn 10 & 2556
        audioVisualizer.setDensity(100);
        //set the audio session permission id from the player
        audioVisualizer.setPlayer(player.getAudioSessionId());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //release the player
//        if (player.isPlaying()){
//            player.stop();
//        }
//        player.release();
        doUnbindService();

    }

    private void doUnbindService() {
        if(isBound){
            unbindService(playerServiceConnection);
            isBound = false;
        }
    }

    private void userResponses() {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED){
            //fetch songs'
            fetchSongs();
        }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (shouldShowRequestPermissionRationale(permission)){
                //show an education UI to user explaining why we need this permission
                //use alert dialog
                new AlertDialog.Builder(this)
                        .setTitle("Requesting Permission")
                        .setMessage("Allow us to fetch songs on your device")
                        .setPositiveButton("allow", new DialogInterface.OnClickListener(){

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //request permission
                                storagePermissionLaucher.launch(permission);
                            }
                        })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Toast.makeText(getApplicationContext(), "You denied us to show songs", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    })
                        .show();
            }
        }
        else {
            Toast.makeText(this, "You canceled to show songs", Toast.LENGTH_SHORT).show();

        }
    }

    private void fetchSongs() {
        //define a list to cary songs
        List<Song> songs = new ArrayList<>();
        Uri mediaStoreUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);

        }else{
            mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        }
        //define projection
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
        };

        //order
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED;
        //get the songs
        try (Cursor cursor = getContentResolver().query(mediaStoreUri, projection, null, null, sortOrder)){
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

            //clear the previous loaded before adding loading again
            while (cursor.moveToNext()){
                //get the values of a given audio file
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                int duration = cursor.getInt(durationColumn);
                int size = cursor.getInt(sizeColumn);
                Long albumId = cursor.getLong(albumIdColumn);
                //song uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                //album artwork uri
                Uri albumArtWorkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);
                //remove .mp3 extension from the song's name

                name = name.substring(0, name.lastIndexOf("."));

                //song item
                Song song =new Song(name, uri, albumArtWorkUri, size, duration);

                //add song item to song list
                songs.add(song);
            }
            //diplay songs
            showSongs(songs);
        }
    }

    private void showSongs(List<Song> songs) {
        if (songs.size() == 0){
            Toast.makeText(this, "No songs", Toast.LENGTH_SHORT).show();
            return;
        }

        //save songs
        allSongs.clear();
        allSongs.addAll(songs);

        //update the tool bar title
        String title = getResources().getString(R.string.app_name) + " - "+songs.size();
        Objects.requireNonNull(getSupportActionBar()).setTitle(title);

        //layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerview.setLayoutManager(layoutManager);

        //songs adapter
        songAdapter = new SongAdapter(this, songs, player, playerView);
        //set the adapter to recyclerview
//        recyclerview.setAdapter(songAdapter);

        //recyclerview animators optional
        ScaleInAnimationAdapter scaleInAnimationAdapter = new ScaleInAnimationAdapter(songAdapter);
        scaleInAnimationAdapter.setDuration(1000);
        scaleInAnimationAdapter.setInterpolator(new OvershootInterpolator());
        scaleInAnimationAdapter.setFirstOnly(false);
        recyclerview.setAdapter(scaleInAnimationAdapter);
    }


    //setting the menu search button

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_btn, menu);
        //search btn item
        MenuItem menuItem = menu.findItem(R.id.searchBtn);

        SearchView seachView = (SearchView) menuItem.getActionView();
        // search song method
        SearchSong(seachView);

        return super.onCreateOptionsMenu(menu);
    }

    private void SearchSong(SearchView searchView) {
        //search view listener

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //filter songs
                filterSong(newText.toLowerCase());
                return false;
            }
        });
    }

    private void filterSong(String query) {
        List<Song> filterdList = new ArrayList<>();
        if (allSongs.size() > 0){
            for (Song song: allSongs){
                if (song.getTitle().toLowerCase().contains(query)){
                    filterdList.add(song);

                }
            }
            if (songAdapter != null){
                songAdapter.filterSongs(filterdList);
            }
        }
    }
}
