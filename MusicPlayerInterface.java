package org.yourcompany.mal;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import javazoom.jl.player.Player;
public class MusicPlayerInterface extends JFrame {
    private final Map<String, PlaylistQueue> playlists;
    private String currentPlaylistName;
    private JList<String> songList;
    private DefaultListModel<String> listModel;
    private JLabel currentSongLabel;
    private JButton addButton, deleteButton, nextButton, prevButton, newPlaylist, deletePlaylist, viewSongsButton, playButton;
    private JComboBox<String> playlistSelector;
    private final ArrayList<String> availableSongs;
    private Player currentPlayer;
    private Thread playerThread;
    private boolean isPlaying = false;
    private boolean isPaused = false;
    private JProgressBar progressBar;
    private FileInputStream currentFIS;
    private long songLengthInBytes = 0;
    private long bytesRead = 0;
    public MusicPlayerInterface() {
        playlists = new HashMap<>();
        availableSongs = loadSongsFromFile("song.txt");
        PlaylistQueue defaultPl = new PlaylistQueue();
        if (!availableSongs.isEmpty()) {
            String[] initialSongs = {"Bohemian Rhapsody - Queen", "Imagine - John Lennon", "Smells Like Teen Spirit - Nirvana"};
            for (String s : initialSongs) {
                if (availableSongs.contains(s)) {
                    defaultPl.enqueue(s);
                }
            }
        }
        playlists.put("Default", defaultPl);
        currentPlaylistName = "Default";
        initUI();
        playlistSelector.addItem("Default");
        playlistSelector.setSelectedItem("Default");
        updateList();
        if (playlists.get("Default").getSize() > 0) {
            playlists.get("Default").setCurrentIndex(0);
            updateCurrentDisplay();
        }
    }
    private ArrayList<String> loadSongsFromFile(String filename) {
        ArrayList<String> songs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    songs.add(line);
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error loading songs from " + filename + ": " + e.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
        }
        if (songs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs found in " + filename + ". Please add songs to the file.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
        }
        return songs;
    }
    private void initUI() {
        setTitle("Music Player - Playlist Implementation");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        playlistSelector = new JComboBox<>();
        JPanel playlistControl = new JPanel();
        playlistControl.add(new JLabel("Playlist:"));
        playlistControl.add(playlistSelector);
        newPlaylist = new JButton("New Playlist");
        playlistControl.add(newPlaylist);
        deletePlaylist = new JButton("Delete Playlist");
        playlistControl.add(deletePlaylist);
        JPanel buttonsPanel = new JPanel();
        addButton = new JButton("Add Song");
        deleteButton = new JButton("Delete Song");
        prevButton = new JButton("Previous");
        nextButton = new JButton("Next");
        playButton = new JButton("▶ Play");
        viewSongsButton = new JButton("View Songs");
        buttonsPanel.add(addButton);
        buttonsPanel.add(deleteButton);
        buttonsPanel.add(prevButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(playButton);
        buttonsPanel.add(viewSongsButton);
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(playlistControl, BorderLayout.NORTH);
        northPanel.add(buttonsPanel, BorderLayout.CENTER);
        JPanel playlistPanel = new JPanel(new BorderLayout());
        listModel = new DefaultListModel<>();
        songList = new JList<>(listModel);
        songList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(songList);
        playlistPanel.add(new JLabel("Playlist:", SwingConstants.CENTER), BorderLayout.NORTH);
        playlistPanel.add(scrollPane, BorderLayout.CENTER);
        JPanel currentPanel = new JPanel(new BorderLayout());
        currentPanel.add(new JLabel("NOW PLAYING:", SwingConstants.CENTER), BorderLayout.NORTH);
        currentSongLabel = new JLabel("No song selected", SwingConstants.CENTER);
        currentSongLabel.setFont(new Font("Arial", Font.BOLD, 14));
        currentSongLabel.setForeground(Color.BLUE);
        currentPanel.add(currentSongLabel, BorderLayout.CENTER);
        // Add progress bar
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0:00 / 0:00");
        progressBar.setPreferredSize(new Dimension(400, 25));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        currentPanel.add(progressPanel, BorderLayout.SOUTH);
        add(northPanel, BorderLayout.NORTH);
        add(playlistPanel, BorderLayout.CENTER);
        add(currentPanel, BorderLayout.SOUTH);
        setupListeners();
    }
    private void setupListeners() {
        addButton.addActionListener(e -> addSong());
        deleteButton.addActionListener(e -> deleteSong());
        nextButton.addActionListener(e -> nextSong());
        prevButton.addActionListener(e -> prevSong());
        playButton.addActionListener(e -> togglePlayPause());
        newPlaylist.addActionListener(e -> createPlaylist());
        deletePlaylist.addActionListener(e -> deletePlaylist());
        viewSongsButton.addActionListener(e -> viewSongs());
        playlistSelector.addActionListener(e -> {
            String selected = (String) playlistSelector.getSelectedItem();
            if (selected != null) {
                currentPlaylistName = selected;
                updateList();
            }
        });
        songList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int sel = songList.getSelectedIndex();
                    if (sel >= 0) {
                        PlaylistQueue pl = getCurrentPlaylist();
                        if (pl != null) {
                            // If a different song is selected while playing, pause the current song
                            if (pl.getCurrentIndex() != sel && (isPlaying || isPaused)) {
                                pauseSong();
                                isPaused = false;
                                SwingUtilities.invokeLater(() -> {
                                    playButton.setText("▶ Play");
                                    playButton.revalidate();
                                    playButton.repaint();
                                });
                            }
                            pl.setCurrentIndex(sel);
                            updateCurrentDisplay();
                        }
                    }
                }
            }
        });
    }
    private void addSong() {
        if (availableSongs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs available to add. Check song.txt file.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Filter out songs already in the current playlist
        PlaylistQueue currentPlaylist = getCurrentPlaylist();
        ArrayList<String> availableToAdd = new ArrayList<>();
        for (String song : availableSongs) {
            if (currentPlaylist == null || !currentPlaylist.getSongs().contains(song)) {
                availableToAdd.add(song);
            }
        }
        if (availableToAdd.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All available songs are already in this playlist!",
                    "No Songs Available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<String> songCombo = new JComboBox<>(availableToAdd.toArray(new String[0]));
        int result = JOptionPane.showConfirmDialog(this, songCombo, "Select Song to Add",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String song = (String) songCombo.getSelectedItem();
            if (song != null) {
                PlaylistQueue pq = getCurrentPlaylist();
                if (pq != null) {
                    pq.enqueue(song);
                    listModel.addElement(song);
                    if (pq.getSize() == 1) {
                        pq.setCurrentIndex(0);
                        updateCurrentDisplay();
                    }
                }
            }
        }
    }
    private void deleteSong() {
        int selected = songList.getSelectedIndex();
        if (selected != -1) {
            PlaylistQueue pq = getCurrentPlaylist();
            if (pq != null) {
                // Stop playback if the deleted song is currently playing
                if (pq.getCurrentIndex() == selected && isPlaying) {
                    stopSong();
                }
                pq.removeSong(selected);
                listModel.remove(selected);
                updateCurrentDisplay();
            }
        }
    }
    private void nextSong() {
        PlaylistQueue pq = getCurrentPlaylist();
        if (pq != null && pq.nextSong()) {
            if (isPlaying || isPaused) {
                pauseSong();  // Pause current song before moving to next
                isPaused = false;  // Reset pause state
                SwingUtilities.invokeLater(() -> {
                    playButton.setText("▶ Play");
                    playButton.revalidate();
                    playButton.repaint();
                });
            }
            updateCurrentDisplay();
        }
    }
    private void prevSong() {
        PlaylistQueue pq = getCurrentPlaylist();
        if (pq != null && pq.prevSong()) {
            if (isPlaying || isPaused) {
                pauseSong();  // Pause current song before moving to previous
                isPaused = false;  // Reset pause state
                SwingUtilities.invokeLater(() -> {
                    playButton.setText("▶ Play");
                    playButton.revalidate();
                    playButton.repaint();
                });
            }
            updateCurrentDisplay();
        }
    }
    private void createPlaylist() {
        String name = JOptionPane.showInputDialog(this, "Enter Playlist Name:");
        if (name != null && !name.trim().isEmpty() && !playlists.containsKey(name)) {
            PlaylistQueue p = new PlaylistQueue();
            playlists.put(name, p);
            playlistSelector.addItem(name);
            playlistSelector.setSelectedItem(name);
            currentPlaylistName = name;
            updateList();
        }
    }
    private void deletePlaylist() {
        if (playlists.size() > 1 && currentPlaylistName != null) {
            playlists.remove(currentPlaylistName);
            playlistSelector.removeItem(currentPlaylistName);
            currentPlaylistName = (String) playlistSelector.getSelectedItem();
            updateList();
        } else {
            JOptionPane.showMessageDialog(this, "Cannot delete the last playlist!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void viewSongs() {
        if (availableSongs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No songs available in song.txt.",
                    "Song List", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextArea textArea = new JTextArea(10, 30);
        textArea.setEditable(false);
        for (String song : availableSongs) {
            textArea.append(song + "\n");
        }
        JScrollPane scrollPane = new JScrollPane(textArea);

        JOptionPane.showMessageDialog(this, scrollPane, "Available Songs", JOptionPane.INFORMATION_MESSAGE);

    }



    private void updateCurrentDisplay() {

        PlaylistQueue pq = getCurrentPlaylist();

        if (pq != null) {

            String current = pq.getCurrentSong();

            currentSongLabel.setText(current != null ? current : "No song selected");

            if (current != null) {

                songList.setSelectedIndex(pq.getCurrentIndex());

                // Don't auto-play, just update the display

            }

        } else {

            currentSongLabel.setText("No playlist selected");

            songList.clearSelection();

        }

    }



    private void updateList() {

        listModel.clear();

        PlaylistQueue pq = getCurrentPlaylist();

        if (pq != null) {

            for (String s : pq.getSongs()) {

                listModel.addElement(s);

            }

        }

        updateCurrentDisplay();

    }



    private PlaylistQueue getCurrentPlaylist() {

        return playlists.get(currentPlaylistName);

    }



    private void stopSong() {

        if (currentPlayer != null) {

            currentPlayer.close();

            currentPlayer = null;

        }

        if (playerThread != null && playerThread.isAlive()) {

            playerThread.interrupt();

            playerThread = null;

        }

        if (currentFIS != null) {

            try {

                currentFIS.close();

            } catch (IOException ex) {

                // Ignore

            }

            currentFIS = null;

        }

        isPlaying = false;

        isPaused = false;

        SwingUtilities.invokeLater(() -> {

            playButton.setText("▶ Play");

            playButton.revalidate();

            playButton.repaint();

        });

        progressBar.setValue(0);

        progressBar.setString("0:00 / 0:00");

    }



    private void togglePlayPause() {

        if (!isPlaying && !isPaused) {

            // Not playing - start playing

            playCurrentSong();

            SwingUtilities.invokeLater(() -> {

                playButton.setText("⏸ Pause");

                playButton.revalidate();

                playButton.repaint();

            });

        } else if (isPlaying && !isPaused) {

            // Currently playing - pause it

            pauseSong();

            SwingUtilities.invokeLater(() -> {

                playButton.setText("▶ Play");

                playButton.revalidate();

                playButton.repaint();

            });

        } else if (isPaused) {

            // Currently paused - restart playing (Note: JLayer doesn't support resume from position)

            isPaused = false;

            SwingUtilities.invokeLater(() -> {

                playButton.setText("⏸ Pause");

                playButton.revalidate();

                playButton.repaint();

            });

            playCurrentSong();

        }

    }



    private void pauseSong() {

        // Pause: Stop the player but keep the state

        isPaused = true;

        if (currentPlayer != null) {

            currentPlayer.close();

            currentPlayer = null;

        }

        if (playerThread != null && playerThread.isAlive()) {

            playerThread.interrupt();

            playerThread = null;

        }

        isPlaying = false;

    }



    private void playCurrentSong() {

        PlaylistQueue pq = getCurrentPlaylist();

        if (pq != null) {

            String current = pq.getCurrentSong();

            if (current != null) {

                // Extract the actual song name (remove the musical note symbols)

                String songName = current.replace("♪ ", "").replace(" ♪", "");

                stopSong();  // Stop any currently playing song

                playSong(songName);

            }

        }

    }



    private void playSong(String songName) {

    try {

        // Convert song name to possible file name

        String expectedFileName = songName.replace(" - ", "_").replace(" ", "_") + ".mp3";

       

        // Search for the file in the current directory

        File dir = new File(".");

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp3"));

       

        File targetFile = null;

        if (files != null) {

            for (File f : files) {

                // Check ignoring case and symbols

                String simplifiedName = f.getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

                String simplifiedExpected = expectedFileName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

                if (simplifiedName.equals(simplifiedExpected)) {

                    targetFile = f;

                    break;

                }

            }

        }



        if (targetFile == null) {

            System.out.println("⚠️ File not found for: " + songName);

            System.out.println("Expected file: " + expectedFileName);

            return;

        }



        System.out.println("▶ Now playing: " + targetFile.getName());

        currentFIS = new FileInputStream(targetFile);

        songLengthInBytes = targetFile.length();

        bytesRead = 0;

        

        currentPlayer = new Player(currentFIS);

        isPlaying = true;

        isPaused = false;

        

        // Estimate song duration (rough estimate: 128kbps = 16000 bytes/sec)

        final long estimatedDuration = songLengthInBytes / 16000; // seconds

        

        // Start progress updater thread

        Thread progressThread = new Thread(() -> {

            long startTime = System.currentTimeMillis();

            while (isPlaying && !Thread.currentThread().isInterrupted()) {

                try {

                    Thread.sleep(100);

                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;

                    int progress = estimatedDuration > 0 ? (int)((elapsed * 100) / estimatedDuration) : 0;

                    if (progress > 100) progress = 100;

                    

                    final int finalProgress = progress;

                    final long finalElapsed = elapsed;

                    

                    SwingUtilities.invokeLater(() -> {

                        progressBar.setValue(finalProgress);

                        String timeStr = formatTime(finalElapsed) + " / " + formatTime(estimatedDuration);

                        progressBar.setString(timeStr);

                    });

                } catch (InterruptedException e) {

                    break;

                }

            }

        });

        progressThread.setDaemon(true);

        progressThread.start();



        playerThread = new Thread(() -> {

            try {

                currentPlayer.play();

                isPlaying = false;

                SwingUtilities.invokeLater(() -> {

                    progressBar.setValue(100);

                    String timeStr = formatTime(estimatedDuration) + " / " + formatTime(estimatedDuration);

                    progressBar.setString(timeStr);

                });

            } catch (Exception ex) {

                if (!(ex instanceof InterruptedException)) {

                    ex.printStackTrace();

                }

            }

        });

        playerThread.start();



    } catch (Exception e) {

        e.printStackTrace();

        isPlaying = false;

    }

}



    private String formatTime(long seconds) {

        long mins = seconds / 60;

        long secs = seconds % 60;

        return String.format("%d:%02d", mins, secs);

    }





    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> new MusicPlayerInterface().setVisible(true));

    }

}