package org.yourcompany.mal;
import java.util.LinkedList;
import java.util.List;

public class PlaylistQueue {
    private final LinkedList<String> songs;
    private int currentIndex;
    
    public PlaylistQueue() {
        songs = new LinkedList<>();
        currentIndex = -1;
    }
    
    public void enqueue(String song) {
        songs.addLast(song);
        if (songs.size() == 1) {
            currentIndex = 0;
        }
    }
    
    public void insertSong(int index, String song) {
        if (index >= 0 && index <= songs.size()) {
            songs.add(index, song);
            if (currentIndex >= index) {
                currentIndex++;
            }
        }
    }
    
    public void removeSong(int index) {
        if (index >= 0 && index < songs.size()) {
            songs.remove(index);
            if (currentIndex == index) {
                currentIndex = -1;
            } else if (currentIndex > index) {
                currentIndex--;
            }
        }
    }
    
    public boolean nextSong() {
        if (songs.size() > 0 && currentIndex < songs.size() - 1) {
            currentIndex++;
            return true;
        }
        return false;
    }
    
    public boolean prevSong() {
        if (currentIndex > 0) {
            currentIndex--;
            return true;
        }
        return false;
    }
    
    public String getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < songs.size()) {
            return "♪ " + songs.get(currentIndex) + " ♪";
        }
        return null;
    }
    
    public int getCurrentIndex() { return currentIndex; }
    public int getSize() { return songs.size(); }
    public void setCurrentIndex(int index) { 
        if (index >= 0 && index < songs.size()) {
            currentIndex = index;
        }
    }
    
    public List<String> getSongs() {
        return songs;
    }
}